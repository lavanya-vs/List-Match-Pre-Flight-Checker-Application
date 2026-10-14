package com.iqvia.preflight.service;

import java.util.Map;
import java.util.function.ObjIntConsumer;

/** Streams parsed data rows without holding the whole file in memory. */
@FunctionalInterface
public interface RowSource {
    void forEach(ObjIntConsumer<Map<String, String>> consumer);
}
