package com.lumoren.agentchat.model;

import com.google.gson.JsonObject;

/**
 * A compact source badge on an {@link ActionCard}: where a fact came from.
 *
 * @param type          evidence source type name (LOCAL_GAME, JEI, FTB_QUESTS, PATCHOULI, WEB)
 * @param label         short display label
 * @param sourceIdOrUrl local id, file source, or http(s) URL (only http/https are clickable)
 * @param verified      whether the content was actually read from the source
 */
public record CardSource(
        String type,
        String label,
        String sourceIdOrUrl,
        boolean verified
) {
    public boolean isWebLink() {
        return sourceIdOrUrl != null
                && (sourceIdOrUrl.startsWith("http://") || sourceIdOrUrl.startsWith("https://"));
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("label", label);
        obj.addProperty("source", sourceIdOrUrl == null ? "" : sourceIdOrUrl);
        obj.addProperty("verified", verified);
        return obj;
    }

    public static CardSource fromJsonObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        String type = obj.has("type") && !obj.get("type").isJsonNull()
                ? obj.get("type").getAsString() : "";
        String label = obj.has("label") && !obj.get("label").isJsonNull()
                ? obj.get("label").getAsString() : "";
        String source = obj.has("source") && !obj.get("source").isJsonNull()
                ? obj.get("source").getAsString() : "";
        boolean verified = obj.has("verified") && obj.get("verified").getAsBoolean();
        return new CardSource(type, label, source, verified);
    }
}
