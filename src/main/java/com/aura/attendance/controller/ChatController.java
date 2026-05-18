package com.aura.attendance.controller;

import com.aura.attendance.entity.*;
import com.aura.attendance.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AURA AI chatbot — powered by Google Gemini 1.5 Flash directly.
 * No Lovable gateway. No OpenAI. Pure Gemini REST API.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    // ── Gemini REST endpoint (generateContent) ──
    private static final String GEMINI_URL ="https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;

    // Reads from application.properties: gemini.api.key
    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    // Reads from application.properties: gemini.model (default: gemini-1.5-flash)
    @Value("${gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    public ChatController(UserRepository userRepository,
                          AttendanceRepository attendanceRepository,
                          SessionRepository sessionRepository) {
        this.userRepository      = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository   = sessionRepository;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body,
                                  @AuthenticationPrincipal UserDetails ud) {
        try {
            // ── 1. Resolve API key (env var takes priority over property) ──
            String key = System.getenv("GEMINI_API_KEY");
            if (key == null || key.isBlank()) key = geminiApiKey;
            if (key == null || key.isBlank()) {
                return ResponseEntity.status(500).body(Map.of(
                    "error", "Gemini API key not configured. Add gemini.api.key to application.properties."));
            }

            // ── 2. Build context from logged-in user's data ──
            User user = (ud != null)
                ? userRepository.findByEmail(ud.getUsername()).orElse(null)
                : null;
            String systemContext = buildSystemPrompt(user);

            // ── 3. Extract incoming messages from frontend ──
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> incoming =
                (List<Map<String, Object>>) body.getOrDefault("messages", List.of());

            // ── 4. Build Gemini `contents` array ──
            //    Gemini uses: [{role:"user"|"model", parts:[{text:"..."}]}]
            //    First turn: inject system context as first user message
            List<Map<String, Object>> contents = new ArrayList<>();

            // System context as first user message (Gemini doesn't have a system role)
            contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", systemContext))
            ));
            // Fake model acknowledgment so conversation starts correctly
            contents.add(Map.of(
                "role", "model",
                "parts", List.of(Map.of("text", "Understood. I am AURA AI, ready to help with attendance queries."))
            ));

            // Add actual conversation history
            for (Map<String, Object> m : incoming) {
                String role    = Objects.toString(m.get("role"), "user");
                String content = Objects.toString(m.get("content"), "");
                // Gemini uses "model" not "assistant"
                String geminiRole = "assistant".equals(role) ? "model" : "user";
                contents.add(Map.of(
                    "role", geminiRole,
                    "parts", List.of(Map.of("text", content))
                ));
            }

            // ── 5. Build Gemini request payload ──
            Map<String, Object> generationConfig = Map.of(
                "temperature", 0.7,
                "maxOutputTokens", 512,
                "topP", 0.95
            );

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("contents", contents);
            payload.put("generationConfig", generationConfig);

            // ── 6. Call Gemini REST API ──
            String url  = String.format(GEMINI_URL, geminiModel, key);
            String json = toJson(payload);

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            // ── 7. Handle errors ──
            if (resp.statusCode() == 400) {
                System.err.println("[ChatController] Gemini 400: " + resp.body());
                return ResponseEntity.status(500).body(Map.of("error", "Invalid request to Gemini API."));
            }
            if (resp.statusCode() == 403) {
                System.err.println("[ChatController] Gemini 403 — invalid API key");
                return ResponseEntity.status(500).body(Map.of("error", "Gemini API key is invalid or expired. Check gemini.api.key in application.properties."));
            }
            if (resp.statusCode() == 429) {
                return ResponseEntity.status(429).body(Map.of("error", "Gemini quota exceeded. Try again in a moment."));
            }
            if (resp.statusCode() >= 400) {
                System.err.println("[ChatController] Gemini error " + resp.statusCode() + ": " + resp.body());
                return ResponseEntity.status(500).body(Map.of("error", "Gemini API error: " + resp.statusCode()));
            }

            // ── 8. Extract text from Gemini response ──
            String reply = extractGeminiText(resp.body());
            return ResponseEntity.ok(Map.of("reply", reply));

        } catch (Exception e) {
            System.err.println("[ChatController] Exception: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Chat error: " + e.getMessage()));
        }
    }

    // ── Build a rich system prompt with user context ──
    private String buildSystemPrompt(User u) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are AURA AI, a friendly assistant for an Indian engineering college attendance system called AURA.\n");
        sb.append("Answer concisely. Use bullet points when listing items. Never reveal raw tokens or fingerprints.\n");
        sb.append("If asked something unrelated to attendance, gently redirect to attendance topics.\n\n");

        if (u == null) {
            sb.append("User: Anonymous (not logged in).\n");
            return sb.toString();
        }

        sb.append("USER CONTEXT:\n");
        sb.append("Name: ").append(u.getFullName()).append('\n');
        sb.append("Role: ").append(u.getRole().name()).append('\n');
        if (u.getDepartment() != null) sb.append("Department: ").append(u.getDepartment().name()).append('\n');
        if (u.getYear() != null) sb.append("Year: ").append(u.getYear()).append('\n');
        if (u.getSection() != null) sb.append("Section: ").append(u.getSection()).append('\n');

        if (u.getRole() == User.AppRole.student) {
            List<AttendanceRecord> recs = attendanceRepository.findByStudentId(u.getId());
            long total   = recs.size();
            long present = recs.stream().filter(r ->
                r.getStatus() == AttendanceRecord.AttendanceStatus.PRESENT ||
                r.getStatus() == AttendanceRecord.AttendanceStatus.OD).count();
            long absent  = total - present;
            double pct   = total == 0 ? 0 : (present * 100.0 / total);

            sb.append(String.format("Overall Attendance: %.1f%% (%d/%d classes present)\n", pct, present, total));
            if (total > 0) {
                long need = (long) Math.ceil(0.75 * total) - present;
                if (need > 0) {
                    sb.append("Warning: Need ").append(need).append(" more classes to reach 75%.\n");
                } else {
                    long canSkip = (long) Math.floor((present - 0.75 * total) / 0.75);
                    sb.append("Can still skip: ").append(Math.max(canSkip, 0)).append(" class(es) and stay above 75%.\n");
                }
            }
            sb.append("Absent count: ").append(absent).append('\n');

            // Per-subject breakdown
            Map<String, long[]> bySub = new LinkedHashMap<>();
            for (AttendanceRecord r : recs) {
                String name = r.getSession().getSubject().getName();
                bySub.computeIfAbsent(name, k -> new long[]{0, 0});
                bySub.get(name)[1]++;
                if (r.getStatus() != AttendanceRecord.AttendanceStatus.ABSENT) bySub.get(name)[0]++;
            }
            if (!bySub.isEmpty()) {
                sb.append("Subject-wise attendance:\n");
                bySub.forEach((name, st) -> {
                    double subPct = st[1] == 0 ? 0 : (st[0] * 100.0 / st[1]);
                    sb.append(String.format("  - %s: %d/%d (%.0f%%)\n", name, st[0], st[1], subPct));
                });
            }
        } else if (u.getRole() == User.AppRole.teacher) {
            long sessions = sessionRepository.findByTeacherIdOrderByStartedAtDesc(u.getId()).size();
            sb.append("Sessions conducted: ").append(sessions).append('\n');
            sb.append("Tip: Mark = manual present, OD = on duty, Unmark = undo manual mark.\n");
        } else {
            long s = userRepository.countByRole(User.AppRole.student);
            long t = userRepository.countByRole(User.AppRole.teacher);
            sb.append("Platform stats: ").append(s).append(" students, ").append(t).append(" teachers.\n");
        }
        return sb.toString();
    }

    /**
     * Extract text from Gemini generateContent response:
     * {"candidates":[{"content":{"parts":[{"text":"..."}],"role":"model"},...}]}
     */
    private static String extractGeminiText(String json) {
        try {
            // Find "text": inside candidates[0].content.parts[0]
            int textIdx = json.indexOf("\"text\"");
            if (textIdx < 0) {
                System.err.println("[ChatController] No 'text' in Gemini response: " + json);
                return "Sorry, I couldn't generate a reply. Please try again.";
            }
            int colon = json.indexOf(':', textIdx);
            int q1    = json.indexOf('"', colon + 1);
            if (q1 < 0) return "Sorry, I couldn't generate a reply.";

            StringBuilder sb = new StringBuilder();
            for (int i = q1 + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char n = json.charAt(++i);
                    switch (n) {
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            if (i + 4 < json.length()) {
                                sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            }
                            break;
                        default: sb.append(n);
                    }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            String result = sb.toString().trim();
            return result.isEmpty() ? "Sorry, I couldn't generate a reply." : result;
        } catch (Exception e) {
            System.err.println("[ChatController] Parse error: " + e.getMessage());
            return "Sorry, I couldn't parse the AI response.";
        }
    }

    // ── Minimal JSON serializer (no extra deps) ──
    @SuppressWarnings("unchecked")
    private static String toJson(Object o) {
        if (o == null) return "null";
        if (o instanceof String s) return "\"" + escapeJson(s) + "\"";
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        if (o instanceof Map<?, ?> m) {
            return "{" + ((Map<String, Object>) m).entrySet().stream()
                .map(e -> "\"" + escapeJson(e.getKey()) + "\":" + toJson(e.getValue()))
                .collect(Collectors.joining(",")) + "}";
        }
        if (o instanceof List<?> l) {
            return "[" + l.stream().map(ChatController::toJson).collect(Collectors.joining(",")) + "]";
        }
        return "\"" + escapeJson(o.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}