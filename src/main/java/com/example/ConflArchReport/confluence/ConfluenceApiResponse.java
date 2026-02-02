package com.example.ConflArchReport.confluence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluenceApiResponse {

    private String id;
    private String type;
    private String title;
    private Map<String, BodyRepresentation> body;
    private Map<String, ChildrenWrapper> children;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Map<String, BodyRepresentation> getBody() { return body; }
    public void setBody(Map<String, BodyRepresentation> body) { this.body = body; }
    public Map<String, ChildrenWrapper> getChildren() { return children; }
    public void setChildren(Map<String, ChildrenWrapper> children) { this.children = children; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BodyRepresentation {
        private String value;
        private String representation;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getRepresentation() { return representation; }
        public void setRepresentation(String representation) { this.representation = representation; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChildrenWrapper {
        private List<ChildRef> results;

        public List<ChildRef> getResults() { return results; }
        public void setResults(List<ChildRef> results) { this.results = results; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChildRef {
        private String id;
        private String title;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
