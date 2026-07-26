package com.lumoren.agentchat.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured, user-facing summary of a tool result.
 *
 * @param type       card category
 * @param title      concise heading
 * @param summary    short result summary
 * @param facts      verified facts produced by tools
 * @param actions    suggested executable actions
 * @param sourceTool tool that produced this card
 * @param sources    compact evidence source badges (may be empty)
 */
public record ActionCard(
        Type type,
        String title,
        String summary,
        List<String> facts,
        List<AssistantAction> actions,
        String sourceTool,
        List<CardSource> sources
) {
    public ActionCard {
        facts = facts == null ? List.of() : List.copyOf(facts);
        actions = actions == null ? List.of() : List.copyOf(actions);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /** Backward-compatible constructor without source badges. */
    public ActionCard(Type type, String title, String summary, List<String> facts,
                      List<AssistantAction> actions, String sourceTool) {
        this(type, title, summary, facts, actions, sourceTool, List.of());
    }

    public enum Type {
        PROJECT,
        MATERIALS,
        RECIPE,
        WAYPOINT,
        STATUS,
        GUIDE_STEP,
        DIAGNOSIS,
        SOURCE_SUMMARY
    }

    public JsonObject toJsonObject() {
        JsonObject card = new JsonObject();
        card.addProperty("type", type.name());
        card.addProperty("title", title);
        card.addProperty("summary", summary);
        card.addProperty("source_tool", sourceTool);

        JsonArray factsArray = new JsonArray();
        for (String fact : facts) {
            factsArray.add(fact);
        }
        card.add("facts", factsArray);

        JsonArray actionsArray = new JsonArray();
        for (AssistantAction action : actions) {
            actionsArray.add(action.toJsonObject());
        }
        card.add("actions", actionsArray);

        if (!sources.isEmpty()) {
            JsonArray sourcesArray = new JsonArray();
            for (CardSource source : sources) {
                sourcesArray.add(source.toJsonObject());
            }
            card.add("sources", sourcesArray);
        }
        return card;
    }

    public String toToolResultContent() {
        JsonObject root = new JsonObject();
        root.add("action_card", toJsonObject());
        return root.toString();
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type.name()).append("] ").append(nullToEmpty(title));
        if (summary != null && !summary.isBlank()) {
            sb.append("\n").append(summary);
        }
        for (String fact : facts) {
            if (fact != null && !fact.isBlank()) {
                sb.append("\n- ").append(fact);
            }
        }
        if (!actions.isEmpty()) {
            sb.append("\nActions: ");
            for (int i = 0; i < actions.size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                sb.append(actions.get(i).label());
            }
        }
        return sb.toString();
    }

    public static ActionCard fromToolResultContent(String content) {
        try {
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            if (!root.has("action_card") || !root.get("action_card").isJsonObject()) {
                return null;
            }
            return fromJsonObject(root.getAsJsonObject("action_card"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static ActionCard fromJsonObject(JsonObject obj) {
        Type type = obj.has("type") && !obj.get("type").isJsonNull()
                ? parseType(obj.get("type").getAsString()) : Type.STATUS;
        String title = obj.has("title") && !obj.get("title").isJsonNull()
                ? obj.get("title").getAsString() : "";
        String summary = obj.has("summary") && !obj.get("summary").isJsonNull()
                ? obj.get("summary").getAsString() : "";
        String sourceTool = obj.has("source_tool") && !obj.get("source_tool").isJsonNull()
                ? obj.get("source_tool").getAsString() : "";

        List<String> facts = new ArrayList<>();
        if (obj.has("facts") && obj.get("facts").isJsonArray()) {
            for (var element : obj.getAsJsonArray("facts")) {
                if (!element.isJsonNull()) {
                    facts.add(element.getAsString());
                }
            }
        }

        List<AssistantAction> actions = new ArrayList<>();
        if (obj.has("actions") && obj.get("actions").isJsonArray()) {
            for (var element : obj.getAsJsonArray("actions")) {
                if (element.isJsonObject()) {
                    try {
                        AssistantAction action = AssistantAction.fromJsonObject(element.getAsJsonObject());
                        if (action != null) {
                            actions.add(action);
                        }
                    } catch (RuntimeException ignored) {
                        // Ignore malformed actions; keep the card itself renderable.
                    }
                }
            }
        }
        return new ActionCard(type, title, summary, facts, actions, sourceTool, parseSources(obj));
    }

    private static List<CardSource> parseSources(JsonObject obj) {
        List<CardSource> sources = new ArrayList<>();
        if (obj.has("sources") && obj.get("sources").isJsonArray()) {
            for (var element : obj.getAsJsonArray("sources")) {
                if (element.isJsonObject()) {
                    CardSource source = CardSource.fromJsonObject(element.getAsJsonObject());
                    if (source != null) {
                        sources.add(source);
                    }
                }
            }
        }
        return sources;
    }

    private static Type parseType(String value) {
        try {
            return Type.valueOf(value);
        } catch (RuntimeException e) {
            return Type.STATUS;
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
