/*
 * Copyright 2026 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 */
package oshi.ffm;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prints every native struct mapping in {@code oshi-core-ffm} with its computed offsets, so a CI run can be read
 * against the platform's own headers instead of trusted because the tests passed.
 * <p>
 * Two kinds of mapping are dumped. A {@link MemoryLayout} constant is printed as a tree of members with absolute
 * offsets, in the shape a compiler's record layout dump produces: {@code clang -Xclang -fdump-record-layouts} on macOS
 * and Linux, {@code cl /d1reportAllClassLayout} on Windows. A hand-written {@code OFFSET_} constant is printed as a
 * bare number and flagged, because nothing computes it and nothing checks it.
 * <p>
 * Layout constants are pure arithmetic and resolve on any platform, so a Windows mapping can be dumped from Linux.
 * Classes that also declare native handles fail to initialize away from their own platform; those are listed as
 * skipped, which is why this is worth running on every OS in the matrix rather than only where a mapping is used.
 */
public final class LayoutDump {

    private static final Logger logger = LoggerFactory.getLogger(LayoutDump.class);

    private LayoutDump() {
    }

    /**
     * Prints the layout dump.
     *
     * @param args ignored
     * @throws IOException        if the class files cannot be enumerated
     * @throws URISyntaxException if the code source location is malformed
     */
    public static void main(String @Nullable [] args) throws IOException, URISyntaxException {
        List<String> lines = new ArrayList<>();
        lines.add("os.name=" + System.getProperty("os.name") + " os.arch=" + System.getProperty("os.arch")
                + " java.version=" + System.getProperty("java.version"));

        Path root = Path.of(ForeignFunctions.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> names = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".class")).map(p -> root.relativize(p).toString())
                    .map(s -> s.substring(0, s.length() - ".class".length()).replace(File.separatorChar, '.'))
                    .filter(s -> s.startsWith("oshi.") && s.indexOf('$') < 0 && !s.endsWith("module-info")).sorted()
                    .forEach(names::add);
        }

        int layouts = 0;
        int constants = 0;
        int hidden = 0;
        List<String> skipped = new ArrayList<>();
        for (String name : names) {
            Class<?> clazz;
            try {
                clazz = Class.forName(name, true, LayoutDump.class.getClassLoader());
            } catch (Throwable t) {
                // A mapping class that also declares downcall handles cannot initialize off its own platform
                skipped.add(name + " (" + t.getClass().getSimpleName() + ")");
                continue;
            }
            List<Field> fields = new ArrayList<>();
            List<String> unreadable = new ArrayList<>();
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())
                        || !(MemoryLayout.class.isAssignableFrom(f.getType()) || f.getName().startsWith("OFFSET_"))) {
                    continue;
                }
                // Reading a non-public field would need setAccessible, which forbidden-apis bans. Name it instead,
                // so a mapping is never dropped from the dump without saying so.
                if (Modifier.isPublic(f.getModifiers())) {
                    fields.add(f);
                } else {
                    unreadable.add(f.getName());
                }
            }
            if (fields.isEmpty() && unreadable.isEmpty()) {
                continue;
            }
            fields.sort(Comparator.comparing(Field::getName));
            lines.add("");
            lines.add("=== " + name);
            for (Field f : fields) {
                try {
                    Object value = f.get(null);
                    if (value instanceof MemoryLayout layout) {
                        layouts++;
                        lines.add(String.format(Locale.ROOT, "  %s  [size=%d align=%d]", f.getName(), layout.byteSize(),
                                layout.byteAlignment()));
                        describe(lines, layout, 0L, 4);
                    } else if (value instanceof Number n) {
                        constants++;
                        lines.add(String.format(Locale.ROOT, "  %-38s = %-6s  hand-written, no arithmetic to check it",
                                f.getName(), n));
                    }
                } catch (Throwable t) {
                    lines.add(String.format(Locale.ROOT, "  %-38s !! %s", f.getName(), t));
                }
            }
            if (!unreadable.isEmpty()) {
                hidden += unreadable.size();
                lines.add("  not public, value not read: " + String.join(", ", unreadable));
            }
        }

        lines.add("");
        lines.add(layouts + " layouts and " + constants + " hand-written constants dumped, " + hidden
                + " constants named but not readable");
        if (!skipped.isEmpty()) {
            lines.add("not loadable on this platform, dump elsewhere:");
            skipped.forEach(s -> lines.add("  " + s));
        }
        lines.forEach(logger::info);
    }

    private static void describe(List<String> lines, MemoryLayout layout, long base, int indent) {
        if (!(layout instanceof GroupLayout group)) {
            return;
        }
        boolean isStruct = group instanceof StructLayout;
        long offset = base;
        for (MemoryLayout member : group.memberLayouts()) {
            String label = member.name().orElse(member instanceof PaddingLayout ? "(padding)" : "(anonymous)");
            lines.add(String.format(Locale.ROOT, "%" + indent + "s%6d | %-30s %s", "", offset, label, size(member)));
            if (member instanceof GroupLayout) {
                describe(lines, member, offset, indent + 2);
            }
            // A union overlays its members at the same offset; only a struct advances
            if (isStruct) {
                offset += member.byteSize();
            }
        }
    }

    private static String size(MemoryLayout layout) {
        if (layout instanceof SequenceLayout seq) {
            return seq.elementCount() + " x " + seq.elementLayout().byteSize() + "B = " + seq.byteSize();
        }
        return String.valueOf(layout.byteSize());
    }
}
