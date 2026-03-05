package org.designpatterns.creational.prototype;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry that holds prototype instances and creates clones on demand.
 */
public class DocumentRegistry {

    private final Map<String, Document> prototypes = new HashMap<>();

    public void registerPrototype(String key, Document prototype) {
        prototypes.put(key, prototype);
    }

    public Document createDocument(String key) {
        Document prototype = prototypes.get(key);
        if (prototype == null) {
            throw new IllegalArgumentException("No prototype registered for: " + key);
        }
        return prototype.cloneDocument();
    }
}
