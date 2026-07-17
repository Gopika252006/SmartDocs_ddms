package com.smartdocs.service;

import com.smartdocs.dto.DocResponse;
import com.smartdocs.entity.*;
import com.smartdocs.exception.BadRequestException;
import com.smartdocs.exception.ResourceNotFoundException;
import com.smartdocs.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Transactional
public class AIServiceImpl implements AIService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShareRepository shareRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private Environment env;

    @Autowired
    private DocumentAiMetadataRepository aiMetadataRepository;

    private String selectedModel = "models/gemini-2.5-flash";

    @jakarta.annotation.PostConstruct
    public void init() {
        String apiKey = env.getProperty("gemini.api.key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("[Gemini Discovery] API key not found in configuration.");
            return;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
        List<String> candidates = new ArrayList<>();
        try {
            System.out.println("[Gemini Discovery] Querying Google's ListModels endpoint...");
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> modelsList = (List<Map<String, Object>>) response.getBody().get("models");
                if (modelsList != null) {
                    for (Map<String, Object> modelInfo : modelsList) {
                        String name = (String) modelInfo.get("name");
                        List<String> methods = (List<String>) modelInfo.get("supportedGenerationMethods");
                        if (name != null && methods != null && methods.contains("generateContent")) {
                            candidates.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Gemini Discovery] Failed to dynamically query ListModels endpoint: " + e.getMessage());
        }

        List<String> sortedCandidates = new ArrayList<>();
        for (String c : candidates) {
            if (c.toLowerCase().contains("flash") && !c.toLowerCase().contains("2.5-flash")) {
                sortedCandidates.add(c);
            }
        }
        for (String c : candidates) {
            if (c.toLowerCase().contains("2.5-flash")) {
                sortedCandidates.add(c);
            }
        }
        for (String c : candidates) {
            if (!c.toLowerCase().contains("flash")) {
                sortedCandidates.add(c);
            }
        }

        String configModel = env.getProperty("gemini.model");
        if (configModel != null && !configModel.trim().isEmpty()) {
            String sanitizedConfig = configModel.startsWith("models/") ? configModel : "models/" + configModel;
            sortedCandidates.remove(sanitizedConfig);
            sortedCandidates.add(0, sanitizedConfig);
        }

        if (!sortedCandidates.contains("models/gemini-2.5-flash")) {
            sortedCandidates.add("models/gemini-2.5-flash");
        }

        System.out.println("[Gemini Discovery] Found " + sortedCandidates.size() + " candidate models to test.");
        boolean verified = false;
        for (String candidate : sortedCandidates) {
            System.out.println("[Gemini Discovery] Testing candidate model: " + candidate);
            this.selectedModel = candidate;
            try {
                boolean ok = testConnectionInternal(apiKey);
                if (ok) {
                    System.out.println("[Gemini Discovery] Successful connection verified for model: " + candidate);
                    verified = true;
                    break;
                }
            } catch (Exception e) {
                System.err.println("[Gemini Discovery] Candidate model " + candidate + " failed connection check: " + e.getMessage());
            }
        }

        if (!verified) {
            String fallbackModel = "models/gemini-2.5-flash";
            if (configModel != null && !configModel.trim().isEmpty()) {
                fallbackModel = configModel.startsWith("models/") ? configModel : "models/" + configModel;
            }
            this.selectedModel = fallbackModel;
            System.err.println("[Gemini Discovery] WARNING: All tested candidates failed. Defaulting to fallback: " + this.selectedModel);
        }
    }

    private boolean testConnectionInternal(String apiKey) {
        String url = "https://generativelanguage.googleapis.com/v1beta/" + this.selectedModel + ":generateContent?key=" + apiKey;
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", "Reply with OK");
            parts.add(textPart);
            content.put("parts", parts);
            contents.add(content);
            requestBody.put("contents", contents);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                List candidates = (List) body.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map candidate = (Map) candidates.get(0);
                    Map contentRes = (Map) candidate.get("content");
                    if (contentRes != null) {
                        List partsRes = (List) contentRes.get("parts");
                        if (partsRes != null && !partsRes.isEmpty()) {
                            Map part = (Map) partsRes.get(0);
                            String text = (String) part.get("text");
                            return text != null && text.trim().toUpperCase().contains("OK");
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return false;
    }

    @Override
    public String getSelectedModel() {
        return this.selectedModel;
    }

    // REST connection helper for Gemini
    private String callGemini(String prompt, String mimeType, byte[] fileData) {
        String apiKey = env.getProperty("gemini.api.key");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("Gemini API key is not configured in application.properties!");
            return "{\"summary\":\"AI summary not available (API key missing).\",\"highlights\":[],\"keywords\":[],\"suggestedCategory\":\"General\",\"metadata\":{}}";
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/" + this.selectedModel + ":generateContent?key=" + apiKey;
        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(10000); // 10.0s connect timeout
            requestFactory.setReadTimeout(30000);    // 30.0s read timeout
            RestTemplate restTemplate = new RestTemplate(requestFactory);

            Map<String, Object> requestBody = new HashMap<>();
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();
            List<Map<String, Object>> parts = new ArrayList<>();

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);
            parts.add(textPart);

            if (fileData != null && mimeType != null) {
                Map<String, Object> filePart = new HashMap<>();
                Map<String, Object> inlineData = new HashMap<>();
                inlineData.put("mimeType", mimeType);
                inlineData.put("data", Base64.getEncoder().encodeToString(fileData));
                filePart.put("inlineData", inlineData);
                parts.add(filePart);
            }

            content.put("parts", parts);
            contents.add(content);
            requestBody.put("contents", contents);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                List candidates = (List) body.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map candidate = (Map) candidates.get(0);
                    Map contentRes = (Map) candidate.get("content");
                    if (contentRes != null) {
                        List partsRes = (List) contentRes.get("parts");
                        if (partsRes != null && !partsRes.isEmpty()) {
                            Map part = (Map) partsRes.get(0);
                            return (String) part.get("text");
                        }
                    }
                }
            }
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            String errorBody = ex.getResponseBodyAsString();
            System.err.println("Gemini API Error: " + errorBody);
            try {
                ObjectMapper mapper = new ObjectMapper();
                Map map = mapper.readValue(errorBody, Map.class);
                Map errorMap = (Map) map.get("error");
                if (errorMap != null && errorMap.get("message") != null) {
                    throw new BadRequestException("Gemini API Error: " + errorMap.get("message"));
                }
            } catch (BadRequestException bre) {
                throw bre;
            } catch (Exception e) {
                // Ignore parsing errors
            }
            throw new BadRequestException("Gemini API call failed with status: " + ex.getStatusCode() + ". " + ex.getStatusText());
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            System.err.println("Error calling Gemini API: " + e.getMessage());
            throw new BadRequestException("Gemini API connection error: " + e.getMessage());
        }
        return null;
    }

    private String cleanJsonString(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "{}";
        String clean = raw.trim();
        
        // Extract object block first
        int startObj = clean.indexOf('{');
        int endObj = clean.lastIndexOf('}');
        if (startObj != -1 && endObj != -1 && endObj > startObj) {
            return clean.substring(startObj, endObj + 1);
        }
        
        // Extract array block second
        int startArr = clean.indexOf('[');
        int endArr = clean.lastIndexOf(']');
        if (startArr != -1 && endArr != -1 && endArr > startArr) {
            return clean.substring(startArr, endArr + 1);
        }
        
        return "{}";
    }

    // ----------------------------------------------------
    // Document Text Extraction Core
    // ----------------------------------------------------

    private String extractTextFromPdf(File file) {
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            System.err.println("PDFBox text extraction failed: " + e.getMessage());
        }
        return null;
    }

    private String extractTextFromDocx(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument(fis);
             org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception e) {
            System.err.println("Apache POI text extraction failed: " + e.getMessage());
        }
        return null;
    }

    private String extractTextFromImage(File file, String fileType) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String mimeType = "image/" + fileType.toLowerCase();
            if (fileType.equalsIgnoreCase("jpg") || fileType.equalsIgnoreCase("jpeg")) {
                mimeType = "image/jpeg";
            }
            String prompt = "Extract all text from this image. Output only the extracted text. If no text is found, output nothing.";
            return callGemini(prompt, mimeType, bytes);
        } catch (Exception e) {
            System.err.println("Gemini Image OCR failed: " + e.getMessage());
        }
        return null;
    }

    private String extractTextFromScannedPdf(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String prompt = "Perform OCR on this PDF. Extract all visible text and output only the extracted text.";
            return callGemini(prompt, "application/pdf", bytes);
        } catch (Exception e) {
            System.err.println("Gemini PDF OCR failed: " + e.getMessage());
        }
        return null;
    }

    // ----------------------------------------------------
    // Document Intelligence Processing
    // ----------------------------------------------------

    @Override
    @Transactional
    public void processDocument(Long documentId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) return;
        
        // Delete/clear old AI metadata
        doc.setAiSummary("");
        doc.setAiHighlights("");
        doc.setAiKeywords("");
        doc.setAiMetadata("{}");
        doc.setAiCategory("");
        doc.setSensitive(false);
        doc.setPiiDetected(false);
        doc.setConfidentialityClass("Public");
        doc.setSensitiveDetails("");
        doc.setRelatedDocumentIds("");
        documentRepository.saveAndFlush(doc);

        // Delete from separate table
        aiMetadataRepository.findByDocument(doc).ifPresent(meta -> aiMetadataRepository.delete(meta));
        aiMetadataRepository.flush();

        processDocumentEntity(doc);
    }

    @Override
    @Transactional
    public void processDocumentEntity(Document doc) {
        // Check separate table first (Cache verification)
        DocumentAiMetadata cached = aiMetadataRepository.findByDocument(doc).orElse(null);
        if (cached != null && "COMPLETED".equalsIgnoreCase(cached.getStatus())) {
            doc.setAiSummary(cached.getSummary());
            doc.setAiKeywords(cached.getKeywords());
            doc.setAiHighlights(cached.getImportantPoints());
            doc.setAiCategory(cached.getCategory());
            doc.setAiMetadata("{\"cached\":true}");
            doc.setConfidentialityClass(doc.getConfidentialityClass() != null ? doc.getConfidentialityClass() : "Public");
            doc.setSensitive(!"Public".equalsIgnoreCase(doc.getConfidentialityClass()) || (cached.getImportantPoints() != null && cached.getImportantPoints().toLowerCase().contains("pii")) || doc.isSensitive());
            documentRepository.save(doc);
            return;
        }

        File file = new File(doc.getFilePath());
        if (!file.exists()) return;

        String extracted = "";
        String fileType = doc.getFileType().toLowerCase();
        boolean isMedia = false;
        if (fileType.equals("pdf")) {
            extracted = extractTextFromPdf(file);
            if (extracted == null || extracted.trim().length() < 100) {
                String scanned = extractTextFromScannedPdf(file);
                if (scanned != null && !scanned.trim().isEmpty()) {
                    extracted = scanned;
                }
            }
        } else if (fileType.equals("docx")) {
            extracted = extractTextFromDocx(file);
        } else if (fileType.equals("png") || fileType.equals("jpg") || fileType.equals("jpeg") || fileType.equals("webp") || fileType.equals("bmp") || fileType.equals("gif")) {
            extracted = extractTextFromImage(file, fileType);
        } else if (fileType.equals("txt") || fileType.equals("java") || fileType.equals("py") || fileType.equals("js") || fileType.equals("cpp") || fileType.equals("c") || fileType.equals("html") || fileType.equals("css")) {
            try {
                extracted = Files.readString(file.toPath());
            } catch (Exception e) {
                extracted = "";
            }
        } else if (fileType.equals("mp3") || fileType.equals("wav") || fileType.equals("aac") || fileType.equals("m4a") || fileType.equals("ogg") ||
                   fileType.equals("mp4") || fileType.equals("avi") || fileType.equals("mov") || fileType.equals("mkv") || fileType.equals("webm")) {
            isMedia = true;
            extracted = "";
        } else if (fileType.equals("doc") || fileType.equals("xls") || fileType.equals("xlsx") || fileType.equals("ppt") || fileType.equals("pptx")) {
            extracted = "";
        }

        if (extracted == null) extracted = "";
        doc.setExtractedText(extracted);

        // Analyze content
        String contentToAnalyze = extracted.trim();
        if (contentToAnalyze.isEmpty()) {
            contentToAnalyze = "Filename: " + doc.getName();
        }
        if (contentToAnalyze.length() > 6000) {
            contentToAnalyze = contentToAnalyze.substring(0, 6000);
        }

        List<Folder> folders;
        if (doc.getWorkspace() != null) {
            folders = folderRepository.findByWorkspaceAndDeletedAtIsNull(doc.getWorkspace());
        } else {
            folders = folderRepository.findByUserAndDeletedAtIsNull(doc.getUser());
        }
        String folderNamesList = folders.isEmpty() ? "None" : folders.stream().map(Folder::getName).collect(Collectors.joining(", "));

        String workspaceType = doc.getWorkspace() != null ? doc.getWorkspace().getWorkspaceType() : "GENERAL";
        String prompt;

        if (isMedia) {
            String mediaTypeStr = (fileType.equals("mp4") || fileType.equals("avi") || fileType.equals("mov") || fileType.equals("mkv") || fileType.equals("webm")) ? "Video" : "Audio";
            prompt = "You are a Media Asset Intelligence and Security system. Analyze this " + mediaTypeStr + " file details:\n" +
                    "Filename: " + doc.getName() + "\n" +
                    "Type: " + doc.getFileType() + "\n" +
                    "Size: " + doc.getFileSize() + " bytes\n\n" +
                    "List of available folders: [" + folderNamesList + "]\n\n" +
                    "Generate analysis as a JSON block. Return ONLY the JSON object. Do not include extra text.\n" +
                    "JSON format:\n" +
                    "{\n" +
                    "  \"summary\": \"concise description of this media asset, explaining what it likely is based on the filename and type context (in 2-3 sentences)\",\n" +
                    "  \"importantPoints\": [\"media analysis placeholder\"],\n" +
                    "  \"keywords\": [\"media\", \"" + fileType + "\", \"asset\"],\n" +
                    "  \"suggestedCategory\": \"" + (mediaTypeStr.equals("Video") ? "Videos" : "Audios") + "\",\n" +
                    "  \"folderRecommendation\": \"None\",\n" +
                    "  \"isSensitive\": false,\n" +
                    "  \"piiDetected\": false,\n" +
                    "  \"confidentialityClass\": \"Public\",\n" +
                    "  \"sensitiveDetails\": \"\"\n" +
                    "}";
        } else {
            prompt = "You are a Document Intelligence and Security system. Analyze this document text from a '" + workspaceType + "' workspace:\n" +
                    "Text:\n" + contentToAnalyze + "\n\n" +
                    "List of available folders: [" + folderNamesList + "]\n\n" +
                    "Generate analysis as a JSON block. Return ONLY the JSON object. Do not include extra text.\n" +
                    "JSON format:\n" +
                    "{\n" +
                    "  \"summary\": \"concise 5-line summary of the document, explaining key points, conclusions, context, and action items in a readable paragraph format\",\n" +
                    "  \"importantPoints\": [\"key highlight 1\", \"key highlight 2\", \"key highlight 3\"],\n" +
                    "  \"keywords\": [\"keyword1\", \"keyword2\", \"keyword3\", \"keyword4\"],\n" +
                    "  \"suggestedCategory\": \"one category name matching this workspace type context (e.g. Projects, Assignments, Certificates, Invoices, Finance, HR, Contracts, Medical Reports, Lab Reports, Legal)\",\n" +
                    "  \"folderRecommendation\": \"suggest the best folder name to move this document to from the available folders list. Output exactly one folder name from the list, or 'None'\",\n" +
                    "  \"isSensitive\": true/false, // Set to true ONLY if document is classified as a Passport, PAN card, Aadhar card, Salary Slip, Payslip, or Medical Report\n" +
                    "  \"piiDetected\": true/false, // Set to true if any Personally Identifiable Information is found: e.g. Phone, Email, Physical Address, Aadhar, PAN, or Passport numbers\n" +
                    "  \"confidentialityClass\": \"Public\" / \"Internal\" / \"Confidential\" / \"Highly Confidential\", // Classify security level based on content (e.g. general info is Public, internal communications are Internal, personal docs are Confidential, sensitive financial/identity slips are Highly Confidential)\n" +
                    "  \"sensitiveDetails\": \"comma-separated list of sensitive/PII items found (e.g. 'Aadhar Number, Email Address, Phone Number' or 'Salary Slip' or 'Passport'). Return empty string if none found\"\n" +
                    "}";
        }

        String geminiResponse = null;
        try {
            geminiResponse = callGemini(prompt, null, null);
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            String errorBody = ex.getResponseBodyAsString();
            System.err.println("[Gemini Analysis Failed]");
            System.err.println("HTTP Status: " + ex.getStatusCode());
            System.err.println("Google Error: " + errorBody);
            System.err.println("Raw Response: " + errorBody);
            System.err.println("Parsing Result: Failed to parse Google HTTP error response");
            System.err.println("Database Save Status: Saving FAILED metadata to database");

            String errorMsg = "AI service is temporarily unavailable. Please try again later. Details: " + ex.getMessage();
            doc.setAiSummary(errorMsg);
            doc.setAiKeywords("Error");
            doc.setAiHighlights("Processing Error");
            doc.setConfidentialityClass("Public");
            documentRepository.save(doc);

            DocumentAiMetadata aiMeta = aiMetadataRepository.findByDocument(doc).orElse(new DocumentAiMetadata());
            aiMeta.setDocument(doc);
            aiMeta.setSummary(errorMsg);
            aiMeta.setKeywords("Error");
            aiMeta.setCategory("General");
            aiMeta.setFolderRecommendation("None");
            aiMeta.setImportantPoints("Error");
            aiMeta.setGeneratedAt(LocalDateTime.now());
            aiMeta.setStatus("FAILED");
            aiMeta.setModelUsed(this.selectedModel);
            aiMetadataRepository.save(aiMeta);
            return;
        } catch (Exception e) {
            System.err.println("[Gemini Analysis Failed]");
            System.err.println("HTTP Status: 500/Internal");
            System.err.println("Google Error: " + e.getMessage());
            System.err.println("Raw Response: N/A");
            System.err.println("Parsing Result: Failed due to connection/system exception");
            System.err.println("Database Save Status: Saving FAILED metadata to database");

            String errorMsg = "AI service is temporarily unavailable. Please try again later. Details: " + e.getMessage();
            doc.setAiSummary(errorMsg);
            doc.setAiKeywords("Error");
            doc.setAiHighlights("Processing Error");
            doc.setConfidentialityClass("Public");
            documentRepository.save(doc);

            DocumentAiMetadata aiMeta = aiMetadataRepository.findByDocument(doc).orElse(new DocumentAiMetadata());
            aiMeta.setDocument(doc);
            aiMeta.setSummary(errorMsg);
            aiMeta.setKeywords("Error");
            aiMeta.setCategory("General");
            aiMeta.setFolderRecommendation("None");
            aiMeta.setImportantPoints("Error");
            aiMeta.setGeneratedAt(LocalDateTime.now());
            aiMeta.setStatus("FAILED");
            aiMeta.setModelUsed(this.selectedModel);
            aiMetadataRepository.save(aiMeta);
            return;
        }

        if (geminiResponse == null || geminiResponse.trim().isEmpty()) {
            System.err.println("[Gemini Analysis Failed]");
            System.err.println("HTTP Status: 200/OK but Response Empty");
            System.err.println("Google Error: Empty output generated");
            System.err.println("Raw Response: empty string");
            System.err.println("Parsing Result: empty");
            System.err.println("Database Save Status: Saving FAILED metadata to database");

            String errorMsg = "AI service is temporarily unavailable. Please try again later. Details: Empty response";
            doc.setAiSummary(errorMsg);
            doc.setAiKeywords("Error");
            doc.setAiHighlights("Processing Timeout");
            doc.setConfidentialityClass("Public");
            documentRepository.save(doc);

            DocumentAiMetadata aiMeta = aiMetadataRepository.findByDocument(doc).orElse(new DocumentAiMetadata());
            aiMeta.setDocument(doc);
            aiMeta.setSummary(errorMsg);
            aiMeta.setKeywords("Error");
            aiMeta.setCategory("General");
            aiMeta.setFolderRecommendation("None");
            aiMeta.setImportantPoints("Error");
            aiMeta.setGeneratedAt(LocalDateTime.now());
            aiMeta.setStatus("FAILED");
            aiMeta.setModelUsed(this.selectedModel);
            aiMetadataRepository.save(aiMeta);
            return;
        }

        String cleanJson = cleanJsonString(geminiResponse);
        String summary = "";
        String highlights = "";
        String keywords = "";
        String suggestedCategoryName = "";
        String folderRecommendation = "None";
        boolean isSensitive = false;
        boolean piiDetected = false;
        String confidentialityClass = "Public";
        String sensitiveDetails = "";

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(cleanJson, Map.class);
            summary = (String) map.get("summary");

            List hList = (List) map.get("importantPoints");
            if (hList == null) {
                hList = (List) map.get("highlights");
            }
            if (hList != null) {
                highlights = String.join(", ", (List<String>) hList);
            }

            List kList = (List) map.get("keywords");
            if (kList != null) {
                keywords = String.join(", ", (List<String>) kList);
            }

            suggestedCategoryName = (String) map.get("suggestedCategory");
            folderRecommendation = (String) map.get("folderRecommendation");
            if (folderRecommendation == null) {
                folderRecommendation = "None";
            }

            if (map.get("isSensitive") != null) {
                isSensitive = (Boolean) map.get("isSensitive");
            }
            if (map.get("piiDetected") != null) {
                piiDetected = (Boolean) map.get("piiDetected");
            }
            if (map.get("confidentialityClass") != null) {
                confidentialityClass = (String) map.get("confidentialityClass");
            }
            if (map.get("sensitiveDetails") != null) {
                sensitiveDetails = (String) map.get("sensitiveDetails");
            }
        } catch (Exception e) {
            System.err.println("Failed to parse Gemini response JSON: " + e.getMessage());
            summary = "Summary processing failed due to model response format. Technical details indexed.";
        }

        doc.setAiSummary(summary);
        doc.setAiHighlights(highlights);
        doc.setAiKeywords(keywords);
        doc.setAiMetadata("{\"cached\":true}");
        doc.setAiCategory(suggestedCategoryName);
        doc.setSensitive(isSensitive);
        doc.setPiiDetected(piiDetected);
        doc.setConfidentialityClass(confidentialityClass);
        doc.setSensitiveDetails(sensitiveDetails);

        if (isSensitive || piiDetected) {
            Notification secNotif = Notification.builder()
                    .user(doc.getUser())
                    .message("Security Alert: Sensitive data/PII detected in document '" + doc.getName() + "'. Marked as " + confidentialityClass + ".")
                    .type("USER")
                    .build();
            notificationRepository.save(secNotif);
        }

        // Build index
        String searchIndexText = doc.getName() + " " +
                suggestedCategoryName + " " +
                keywords + " " +
                summary + " " +
                extracted;
        doc.setSearchIndex(searchIndexText);

        // Run similarity & duplicate detection
        detectDuplicates(doc);

        // Find related files
        findRelatedDocuments(doc);

        // Provision/create category if needed
        if (suggestedCategoryName != null && !suggestedCategoryName.trim().isEmpty()) {
            String trimmedCat = suggestedCategoryName.trim();
            if (doc.getWorkspace() != null) {
                Category cat = categoryRepository.findByNameIgnoreCaseAndWorkspace(trimmedCat, doc.getWorkspace())
                        .orElseGet(() -> {
                            Category newCat = Category.builder()
                                    .name(trimmedCat)
                                    .workspace(doc.getWorkspace())
                                    .build();
                            return categoryRepository.save(newCat);
                        });
                doc.setCategory(cat);
            } else if (doc.getUser() != null) {
                Category cat = categoryRepository.findByNameIgnoreCaseAndUser(trimmedCat, doc.getUser())
                        .orElseGet(() -> {
                            Category newCat = Category.builder()
                                    .name(trimmedCat)
                                    .user(doc.getUser())
                                    .build();
                            return categoryRepository.save(newCat);
                        });
                doc.setCategory(cat);
            }
        }

        documentRepository.save(doc);

        // Save AI Metadata table record
        DocumentAiMetadata aiMeta = aiMetadataRepository.findByDocument(doc).orElse(new DocumentAiMetadata());
        aiMeta.setDocument(doc);
        aiMeta.setSummary(summary);
        aiMeta.setKeywords(keywords);
        aiMeta.setCategory(suggestedCategoryName);
        aiMeta.setFolderRecommendation(folderRecommendation);
        aiMeta.setImportantPoints(highlights);
        aiMeta.setGeneratedAt(LocalDateTime.now());
        aiMeta.setStatus("COMPLETED");
        aiMeta.setModelUsed(this.selectedModel);
        
        try {
            aiMetadataRepository.save(aiMeta);
            System.out.println("✓ Active Model: " + this.selectedModel);
            String apiKey = env.getProperty("gemini.api.key", "");
            String maskedKey = apiKey.length() > 4 ? "********" + apiKey.substring(apiKey.length() - 4) : "****";
            System.out.println("✓ Active API Key Loaded: " + maskedKey);
            System.out.println("✓ Gemini Connection Successful");
            System.out.println("✓ HTTP 200 Received");
            System.out.println("✓ AI Metadata Saved");
            
            // Verify loading from DB
            DocumentAiMetadata loaded = aiMetadataRepository.findByDocument(doc).orElse(null);
            if (loaded != null) {
                System.out.println("✓ AI Metadata Loaded From Database");
            } else {
                System.err.println("Verification Failed: AI Metadata could not be loaded from database!");
            }
        } catch (Exception dbEx) {
            System.err.println("Database Save Status: FAILED to save metadata - " + dbEx.getMessage());
            throw dbEx;
        }
    }

    // Similarity helpers
    private void detectDuplicates(Document doc) {
        if (doc.getWorkspace() == null) return;

        List<Document> otherDocs = documentRepository.findByWorkspace(doc.getWorkspace());
        double maxSimilarity = 0.0;
        Document potentialDuplicate = null;

        String text1 = doc.getExtractedText();
        if (text1 == null || text1.trim().isEmpty()) return;

        for (Document other : otherDocs) {
            if (other.getId().equals(doc.getId()) || other.isDeleted()) continue;
            String text2 = other.getExtractedText();
            if (text2 == null || text2.trim().isEmpty()) continue;

            double sim = cosineSimilarity(text1, text2);
            if (sim > maxSimilarity) {
                maxSimilarity = sim;
                potentialDuplicate = other;
            }
        }

        if (maxSimilarity > 0.85) {
            doc.setDuplicateStatus("POTENTIAL_DUPLICATE");
            doc.setDuplicateScore(maxSimilarity);

            Notification dupNotif = Notification.builder()
                    .user(doc.getUser())
                    .message("Duplicate Warning: " + doc.getName() + " is highly similar (" + String.format("%.0f", maxSimilarity * 100) + "%) to existing document: " + potentialDuplicate.getName())
                    .type("USER")
                    .build();
            notificationRepository.save(dupNotif);
        } else {
            doc.setDuplicateStatus("UNIQUE");
            doc.setDuplicateScore(maxSimilarity);
        }
    }

    private void findRelatedDocuments(Document doc) {
        List<Document> otherDocs;
        if (doc.getWorkspace() != null) {
            otherDocs = documentRepository.findByWorkspaceAndIsDeletedFalse(doc.getWorkspace());
        } else if (doc.getUser() != null) {
            otherDocs = documentRepository.findByUserAndIsDeletedFalse(doc.getUser());
        } else {
            return;
        }

        String text1 = doc.getExtractedText() != null ? doc.getExtractedText() : "";
        String keywords1 = doc.getAiKeywords() != null ? doc.getAiKeywords().toLowerCase() : "";
        Set<String> tags1 = doc.getTags();

        class DocScore implements Comparable<DocScore> {
            Document document;
            double score;

            DocScore(Document document, double score) {
                this.document = document;
                this.score = score;
            }

            @Override
            public int compareTo(DocScore o) {
                return Double.compare(o.score, this.score); // descending
            }
        }

        List<DocScore> scored = new ArrayList<>();

        for (Document other : otherDocs) {
            if (other.getId().equals(doc.getId()) || other.isDeleted()) continue;

            String text2 = other.getExtractedText() != null ? other.getExtractedText() : "";
            String keywords2 = other.getAiKeywords() != null ? other.getAiKeywords().toLowerCase() : "";
            Set<String> tags2 = other.getTags();

            double titleSim = cosineSimilarity(doc.getName(), other.getName());
            double textSim = (text1.isEmpty() || text2.isEmpty()) ? 0.0 : cosineSimilarity(text1, text2);
            double kwOverlap = getKeywordOverlap(keywords1, keywords2);
            double categoryMatch = (doc.getCategory() != null && other.getCategory() != null && doc.getCategory().getId().equals(other.getCategory().getId())) ? 1.0 : 0.0;
            
            double tagSim = 0.0;
            if (tags1 != null && tags2 != null && !tags1.isEmpty()) {
                Set<String> commonTags = new HashSet<>(tags1);
                commonTags.retainAll(tags2);
                tagSim = (double) commonTags.size() / tags1.size();
            }

            double overallScore = (titleSim * 0.25) + (kwOverlap * 0.25) + (textSim * 0.25) + (categoryMatch * 0.15) + (tagSim * 0.1);
            if (overallScore > 0.05) {
                scored.add(new DocScore(other, overallScore));
            }
        }

        Collections.sort(scored);
        List<String> relatedIds = scored.stream()
                .limit(5)
                .map(ds -> ds.document.getId().toString())
                .collect(Collectors.toList());

        doc.setRelatedDocumentIds(String.join(",", relatedIds));
    }

    private double cosineSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) return 0.0;

        Map<String, Integer> v1 = getWordFrequencies(s1);
        Map<String, Integer> v2 = getWordFrequencies(s2);

        Set<String> both = new HashSet<>(v1.keySet());
        both.retainAll(v2.keySet());

        double dotProduct = 0.0;
        for (String w : both) {
            dotProduct += v1.get(w) * v2.get(w);
        }

        double normA = 0.0;
        for (int val : v1.values()) {
            normA += val * val;
        }

        double normB = 0.0;
        for (int val : v2.values()) {
            normB += val * val;
        }

        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Map<String, Integer> getWordFrequencies(String text) {
        Map<String, Integer> freqs = new HashMap<>();
        String[] words = text.toLowerCase().split("\\W+");
        for (String w : words) {
            if (w.length() > 2) {
                freqs.put(w, freqs.getOrDefault(w, 0) + 1);
            }
        }
        return freqs;
    }

    private double getKeywordOverlap(String kw1, String kw2) {
        if (kw1 == null || kw2 == null || kw1.isEmpty() || kw2.isEmpty()) return 0.0;

        Set<String> s1 = Arrays.stream(kw1.split(",\\s*")).map(String::trim).collect(Collectors.toSet());
        Set<String> s2 = Arrays.stream(kw2.split(",\\s*")).map(String::trim).collect(Collectors.toSet());

        int originalSize = s1.size();
        if (originalSize == 0) return 0.0;

        s1.retainAll(s2);
        return (double) s1.size() / originalSize;
    }

    // ----------------------------------------------------
    // Q&A Chat implementation
    // ----------------------------------------------------

    @Override
    public String chatWithDocument(Long documentId, String question, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        // Validate access
        if (doc.getWorkspace() != null) {
            if (!doc.getWorkspace().getId().equals(user.getWorkspace().getId())) {
                throw new BadRequestException("Access denied: Document is not in your workspace");
            }
        } else if (!doc.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied: You do not own this document");
        }

        String context = doc.getExtractedText();
        if (context == null || context.trim().isEmpty()) {
            context = "Document text is empty or could not be extracted.";
        }
        if (context.length() > 8000) {
            context = context.substring(0, 8000);
        }

        String prompt = "You are a secure Document Assistant for SmartDocs.\n" +
                "Answer the user's question using ONLY the provided document context below.\n" +
                "If the answer is not contained in the document context, state that clearly and do not make up information.\n\n" +
                "Document Context:\n" + context + "\n\n" +
                "Question: " + question + "\n" +
                "Answer:";

        return callGemini(prompt, null, null);
    }

    // ----------------------------------------------------
    // Smart Search with intent parsing
    // ----------------------------------------------------

    @Override
    public List<DocResponse> smartSearch(String query, String userEmail) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Log the search action
        try {
            AuditLog audit = AuditLog.builder()
                    .user(user)
                    .action("SEARCH: " + query)
                    .ipAddress("127.0.0.1")
                    .browser("Browser")
                    .os("System")
                    .device("Desktop")
                    .build();
            auditLogRepository.save(audit);
        } catch (Exception e) {
            System.err.println("Failed to log search action: " + e.getMessage());
        }

        Workspace workspace = user.getWorkspace();
        List<Document> allDocs;

        if (user.getRole() == com.smartdocs.entity.Role.SUPER_ADMIN) {
            allDocs = documentRepository.findAll().stream()
                    .filter(d -> !d.isDeleted())
                    .collect(Collectors.toList());
        } else if (workspace != null) {
            if (user.getRole() == com.smartdocs.entity.Role.EMPLOYEE) {
                allDocs = documentRepository.findByWorkspaceAndIsDeletedFalse(workspace).stream()
                        .filter(d -> d.getUser() != null && d.getUser().getId().equals(user.getId()))
                        .collect(Collectors.toList());
            } else {
                allDocs = documentRepository.findByWorkspaceAndIsDeletedFalse(workspace);
            }
        } else {
            allDocs = documentRepository.findByUserAndIsDeletedFalse(user);
        }

        if (allDocs.isEmpty()) {
            return new ArrayList<>();
        }

        // Build document summaries for Gemini evaluation
        StringBuilder docsBlock = new StringBuilder();
        java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (Document d : allDocs) {
            String createdStr = d.getCreatedAt() != null ? d.getCreatedAt().format(dateFormatter) : "unknown";
            docsBlock.append("ID: ").append(d.getId()).append("\n")
                    .append("Name: ").append(d.getName()).append("\n")
                    .append("Category: ").append(d.getCategory() != null ? d.getCategory().getName() : "None").append("\n")
                    .append("UploadedAt: ").append(createdStr).append("\n")
                    .append("Summary: ").append(d.getAiSummary() != null ? d.getAiSummary() : "").append("\n")
                    .append("Keywords: ").append(d.getAiKeywords() != null ? d.getAiKeywords() : "").append("\n\n");
        }

        java.time.LocalDateTime currentDateTime = java.time.LocalDateTime.now();
        String currentStr = currentDateTime.format(dateFormatter);

        String prompt = "You are an advanced Semantic Search Engine for a Document Management System.\n" +
                "Evaluate the relevance of each document listed below to the user query: \"" + query + "\".\n" +
                "Current Server Local Date & Time is: " + currentStr + " (Use this to evaluate time queries like 'yesterday', '2 days ago', 'this week', etc.)\n\n" +
                "Documents List:\n" + docsBlock.toString() + "\n" +
                "Rate each document on a relevance scale of 0 to 100. Return ONLY a valid JSON array of objects sorted in descending order of score. Do not return extra commentary or markdown format.\n" +
                "JSON format:\n" +
                "[\n" +
                "  { \"id\": 1, \"score\": 95 },\n" +
                "  { \"id\": 2, \"score\": 80 }\n" +
                "]";

        Map<Long, Integer> docScores = new HashMap<>();
        // 1. Direct filename matching in Java (guarantees exact & partial filename queries work)
        String queryLower = query.toLowerCase().trim();
        for (Document doc : allDocs) {
            String docNameLower = doc.getName().toLowerCase();
            if (docNameLower.equalsIgnoreCase(queryLower)) {
                docScores.put(doc.getId(), 100);
            } else if (docNameLower.contains(queryLower)) {
                docScores.put(doc.getId(), 80);
            }
        }

        // 2. Query Gemini for semantic search ranking
        String geminiRes = callGemini(prompt, null, null);
        String cleanJson = cleanJsonString(geminiRes);

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> list = mapper.readValue(cleanJson, List.class);
            for (Map<String, Object> item : list) {
                Long id = ((Number) item.get("id")).longValue();
                Integer score = ((Number) item.get("score")).intValue();
                docScores.put(id, Math.max(docScores.getOrDefault(id, 0), score));
            }
        } catch (Exception e) {
            System.err.println("Could not parse semantic search scores JSON: " + e.getMessage() + ". Raw: " + cleanJson);
            // Fallback: token-based matching
            String[] tokens = query.toLowerCase().split("\\s+");
            for (Document doc : allDocs) {
                int count = 0;
                String docName = doc.getName().toLowerCase();
                String categoryName = doc.getCategory() != null ? doc.getCategory().getName().toLowerCase() : "";
                String summary = doc.getAiSummary() != null ? doc.getAiSummary().toLowerCase() : "";
                for (String t : tokens) {
                    if (t.length() > 2) {
                        if (docName.contains(t) || categoryName.contains(t) || summary.contains(t)) {
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    docScores.put(doc.getId(), Math.max(docScores.getOrDefault(doc.getId(), 0), count * 25));
                }
            }
        }

        // Filter and sort allDocs based on Gemini scores
        return allDocs.stream()
                .filter(doc -> docScores.containsKey(doc.getId()) && docScores.get(doc.getId()) > 20)
                .sorted((a, b) -> Integer.compare(docScores.get(b.getId()), docScores.get(a.getId())))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public String suggestCategory(String filename, Set<String> tags, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail).orElse(null);
        if (user == null) return "General";

        String workspaceType = user.getWorkspace() != null ? user.getWorkspace().getWorkspaceType() : "GENERAL";

        String prompt = "Suggest one document category name for filename: \"" + filename + "\" with tags: " + tags +
                " in a workspace of type: \"" + workspaceType + "\".\n" +
                "Output only the category name (e.g. Projects, Invoices, Certificates, Assignments, Medical Reports, HR). Keep it to 1-2 words.";

        String res = callGemini(prompt, null, null);
        if (res == null || res.trim().isEmpty()) {
            return "General";
        }
        return res.trim();
    }

    private DocResponse mapToResponse(Document document) {
        return DocResponse.builder()
                .id(document.getId())
                .name(document.getName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .folderId(document.getFolder() != null ? document.getFolder().getId() : null)
                .folderName(document.getFolder() != null ? document.getFolder().getName() : null)
                .categoryId(document.getCategory() != null ? document.getCategory().getId() : null)
                .categoryName(document.getCategory() != null ? document.getCategory().getName() : null)
                .userId(document.getUser().getId())
                .uploaderName(document.getUser().getName())
                .uploaderEmail(document.getUser().getEmail())
                .tags(document.getTags())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .isDeleted(document.isDeleted())
                .aiSummary(document.getAiSummary())
                .aiHighlights(document.getAiHighlights())
                .aiKeywords(document.getAiKeywords())
                .aiMetadata(document.getAiMetadata())
                .aiCategory(document.getAiCategory())
                .duplicateStatus(document.getDuplicateStatus())
                .duplicateScore(document.getDuplicateScore())
                .relatedDocumentIds(document.getRelatedDocumentIds())
                .isSensitive(document.isSensitive())
                .piiDetected(document.isPiiDetected())
                .confidentialityClass(document.getConfidentialityClass())
                .sensitiveDetails(document.getSensitiveDetails())
                .build();
    }

    @Override
    public String suggestName(Long documentId, String userEmail) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        String prompt = "You are a Smart Document Naming Assistant.\n" +
                "Based on the following document name and its summary, suggest a clean, professional, and descriptive filename (use underscores, e.g. Java_Project_Report.pdf).\n" +
                "Original filename: \"" + doc.getName() + "\"\n" +
                "Document Summary: \"" + (doc.getAiSummary() != null ? doc.getAiSummary() : "Not available") + "\"\n\n" +
                "Output ONLY the new filename with the original extension. Do not include extra text, code block markers, or quotes.";

        String res = callGemini(prompt, null, null);
        if (res == null || res.trim().isEmpty()) {
            return doc.getName();
        }
        return res.trim().replaceAll("`", "").replaceAll("\"", "");
    }

    @Override
    public Map<String, Object> suggestFolder(Long documentId, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        List<Folder> folders;
        if (user.getWorkspace() != null) {
            folders = folderRepository.findByWorkspaceAndDeletedAtIsNull(user.getWorkspace());
        } else {
            folders = folderRepository.findByUserAndDeletedAtIsNull(user);
        }

        Map<String, Object> response = new HashMap<>();
        if (folders.isEmpty()) {
            response.put("folderId", null);
            response.put("folderName", null);
            return response;
        }

        String folderNamesList = folders.stream().map(Folder::getName).collect(Collectors.joining(", "));

        String prompt = "You are a Folder Organization Assistant.\n" +
                "Analyze this document name: \"" + doc.getName() + "\" and its summary: \"" + (doc.getAiSummary() != null ? doc.getAiSummary() : "") + "\".\n" +
                "From this list of available folders: [" + folderNamesList + "], suggest the best folder name to move this document to.\n" +
                "If none of the folders is appropriate, return 'null'.\n" +
                "Output ONLY the name of the folder exactly as it is in the list, or the word 'null' (case-insensitive). Do not write anything else.";

        String res = callGemini(prompt, null, null);
        if (res == null || res.trim().equalsIgnoreCase("null") || res.trim().isEmpty()) {
            response.put("folderId", null);
            response.put("folderName", null);
            return response;
        }

        String cleaned = res.trim().replaceAll("`", "").replaceAll("\"", "");
        for (Folder f : folders) {
            if (f.getName().equalsIgnoreCase(cleaned)) {
                response.put("folderId", f.getId());
                response.put("folderName", f.getName());
                return response;
            }
        }
        response.put("folderId", null);
        response.put("folderName", null);
        return response;
    }

    @Override
    @Transactional
    public DocResponse getOrCreateInsights(Long documentId, String userEmail) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        DocumentAiMetadata aiMeta = aiMetadataRepository.findByDocument(doc).orElse(null);
        if (aiMeta == null || !"COMPLETED".equalsIgnoreCase(aiMeta.getStatus())) {
            try {
                processDocumentEntity(doc);
                aiMeta = aiMetadataRepository.findByDocument(doc).orElse(null);
            } catch (Exception e) {
                // Return fallback details
            }
        }

        if (aiMeta != null) {
            doc.setAiSummary(aiMeta.getSummary());
            doc.setAiKeywords(aiMeta.getKeywords());
            doc.setAiHighlights(aiMeta.getImportantPoints());
            doc.setAiCategory(aiMeta.getCategory());
            doc.setAiMetadata("{\"cached\":true}");
            doc.setConfidentialityClass(aiMeta.getCategory() != null ? doc.getConfidentialityClass() : "Public");
            documentRepository.save(doc);
        }

        return mapToResponse(doc);
    }

    @Override
    public String translateText(String text, String targetLanguage) {
        if (text == null || text.trim().isEmpty()) return "";

        String prompt = "You are a professional language translator.\n" +
                "Translate the following text to " + targetLanguage + ".\n" +
                "Maintain the original meaning, professional tone, and structural formatting.\n" +
                "Text:\n" + text + "\n\n" +
                "Translation:";

        String res = callGemini(prompt, null, null);
        return res != null ? res.trim() : "";
    }

    @Override
    public Map<String, Object> analyzeEnterpriseDoc(Long documentId, String mode, String userEmail) {
        Document doc = documentRepository.findByIdAndIsDeletedFalse(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        String text = doc.getExtractedText();
        if (text == null || text.trim().isEmpty()) {
            text = "No extracted text available for this document.";
        }
        if (text.length() > 6000) {
            text = text.substring(0, 6000);
        }

        String prompt = "";
        if (mode.equalsIgnoreCase("POLICY")) {
            prompt = "Analyze the following policy document text and output a JSON block checking for these mandatory sections: Scope, Purpose, Effective Date, Responsibilities, Signatures.\n" +
                    "Document text:\n" + text + "\n\n" +
                    "Output ONLY a valid JSON object in this format:\n" +
                    "{\n" +
                    "  \"status\": \"COMPLIANT or NON_COMPLIANT\",\n" +
                    "  \"mandatorySections\": {\n" +
                    "    \"Scope\": \"Present or Missing\",\n" +
                    "    \"Purpose\": \"Present or Missing\",\n" +
                    "    \"Effective Date\": \"Present or Missing\",\n" +
                    "    \"Responsibilities\": \"Present or Missing\",\n" +
                    "    \"Signatures\": \"Present or Missing\"\n" +
                    "  },\n" +
                    "  \"remarks\": \"Brief explanation of missing sections or compliance status.\"\n" +
                    "}";
        } else if (mode.equalsIgnoreCase("CONTRACT")) {
            prompt = "Analyze the following contract document text and extract the parties, effective date, expiry date, renewal date, and key payment terms.\n" +
                    "Document text:\n" + text + "\n\n" +
                    "Output ONLY a valid JSON object in this format:\n" +
                    "{\n" +
                    "  \"parties\": \"Parties involved\",\n" +
                    "  \"effectiveDate\": \"Effective Date (or Not found)\",\n" +
                    "  \"expiryDate\": \"Expiry Date (or Not found)\",\n" +
                    "  \"renewalDate\": \"Renewal Date (or Not found)\",\n" +
                    "  \"paymentTerms\": \"Key payment terms details\"\n" +
                    "}";
        } else { // COMPLIANCE
            prompt = "Analyze the following document text and output a compliance evaluation JSON block.\n" +
                    "Document text:\n" + text + "\n\n" +
                    "Output ONLY a valid JSON object in this format:\n" +
                    "{\n" +
                    "  \"score\": 85,\n" +
                    "  \"issues\": [\"list of compliance issues found\"],\n" +
                    "  \"recommendations\": \"recommendations to achieve full compliance\"\n" +
                    "}";
        }

        String res = callGemini(prompt, null, null);
        String cleanJson = cleanJsonString(res);

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(cleanJson, Map.class);
        } catch (Exception e) {
            System.err.println("Failed to parse enterprise analyzer response: " + e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("error", "Failed to parse AI response. Raw output: " + res);
            return fallback;
        }
    }

    @Override
    public String generateWorkspaceInsights(long totalFiles, long totalUsers, long storageUsed, List<Map<String, Object>> categoryStats) {
        if (totalFiles == 0) {
            return "No workspace activity available yet.";
        }
        String categories = categoryStats.stream().map(m -> m.get("category") + ": " + m.get("count")).collect(Collectors.joining(", "));
        String prompt = "You are an AI Workspace Insights Assistant.\n" +
                "Based on these live stats:\n" +
                "- Total Documents: " + totalFiles + "\n" +
                "- Total Workspace Users: " + totalUsers + "\n" +
                "- Storage Consumed: " + (storageUsed / (1024 * 1024)) + " MB\n" +
                "- Document Categories: [" + categories + "]\n\n" +
                "Provide a brief 2-sentence natural language analytics insight explanation explaining recent trends or storage shifts in the workspace (e.g. \"Finance uploads increased by 35% this month, driving a rapid rise in storage usage.\"). Keep it highly executive, premium, and concise. Do not use markdown format or list points.";

        String res = null;
        try {
            res = callGemini(prompt, null, null);
        } catch (Exception e) {
            System.err.println("Failed to generate workspace insights from Gemini: " + e.getMessage());
        }
        if (res == null || res.trim().isEmpty()) {
            return "Workspace storage usage remains stable. Categorized documents show a balanced distribution with minimal duplicate files detected.";
        }
        return res.trim().replaceAll("`", "").replaceAll("\"", "");
    }

    @Override
    public Map<String, Object> getSearchHistory(String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<AuditLog> logs = auditLogRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(l -> l.getAction() != null && l.getAction().startsWith("SEARCH: "))
                .collect(Collectors.toList());

        List<String> recent = logs.stream()
                .map(l -> l.getAction().substring(8).trim())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

        Map<String, Long> freqMap = logs.stream()
                .map(l -> l.getAction().substring(8).trim().toLowerCase())
                .collect(Collectors.groupingBy(q -> q, Collectors.counting()));

        List<String> frequent = freqMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(5)
                .collect(Collectors.toList());

        Map<String, Object> res = new HashMap<>();
        res.put("recentSearches", recent);
        res.put("frequentSearches", frequent);
        return res;
    }

    @Override
    public boolean testConnection() {
        try {
            String res = callGemini("Reply with OK", null, null);
            return res != null && res.trim().toUpperCase().contains("OK");
        } catch (Exception e) {
            System.err.println("Startup connection test exception: " + e.getMessage());
            throw e;
        }
    }
}
