package it.mazzoni.vis.marketdata;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class SourceTracker {

    private static final ThreadLocal<Set<String>> SOURCES =
            ThreadLocal.withInitial(LinkedHashSet::new);

    public void record(String source) {
        SOURCES.get().add(source);
    }

    public String summarize() {
        Set<String> s = SOURCES.get();
        if (s.isEmpty()) return "FMP";
        if (s.size() == 1) return s.iterator().next();
        return String.join("+", s);
    }

    public void clear() {
        SOURCES.remove();
    }
}
