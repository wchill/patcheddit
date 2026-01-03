package app.morphe.extension.boostforreddit.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

// Required because the copy of Jackson databind has had unused code removed.
public class EditableObjectNode extends ObjectNode {
    public EditableObjectNode(JsonNodeFactory nc, Map<String, JsonNode> entries) {
        super(nc);
        _children.putAll(entries);
    }

    public EditableObjectNode(Map<String, JsonNode> entries) {
        this(JsonNodeFactory.instance, entries);
    }

    public EditableObjectNode(JsonNode node) {
        super(JsonNodeFactory.instance);
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            _children.put(entry.getKey(), entry.getValue());
        }
    }

    public EditableObjectNode() {
        super(JsonNodeFactory.instance);
    }

    public static EditableObjectNode wrap(JsonNode node) {
        if (node instanceof EditableObjectNode) {
            return (EditableObjectNode) node;
        }

        return new EditableObjectNode(node);
    }

    public JsonNode set(String fieldName, JsonNode value)
    {
        if (value == null) {
            value = nullNode();
        }
        _children.put(fieldName, value);
        return this;
    }

    public void setIfUnset(String fieldName, JsonNode value)
    {
        if (get(fieldName) == null) {
            set(fieldName, value);
        }
    }

    public String data(String key) {
        JsonNode node = _children.get(key);
        if (node == null) {
            return null;
        } else {
            return node.asText();
        }
    }
}
