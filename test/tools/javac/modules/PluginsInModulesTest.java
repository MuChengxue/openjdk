/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @summary Verify that plugins inside modules works
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build ToolBox ModuleTestBase
 * @run main PluginsInModulesTest
 */

import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.source.util.JavacTask;

public class PluginsInModulesTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new PluginsInModulesTest().runTests();
    }

    private static final String pluginModule1 =
            "module pluginMod1 {\n" +
            "    requires jdk.compiler;\n" +
            "\n" +
            "    provides com.sun.source.util.Plugin\n" +
            "      with mypkg1.SimplePlugin1;\n" +
            "}";

    private static final String plugin1 =
            "package mypkg1;\n" +
            "import com.sun.source.util.JavacTask;\n" +
            "import com.sun.source.util.Plugin;\n" +
            "import com.sun.source.util.TaskEvent;\n" +
            "import com.sun.source.util.TaskListener;\n" +
            "\n" +
            "public class SimplePlugin1 implements Plugin {\n" +
            "\n" +
            "    @Override\n" +
            "    public String getName() {\n" +
            "        return \"simpleplugin1\";\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public void init(JavacTask task, String... args) {\n" +
            "        task.addTaskListener(new PostAnalyzeTaskListener());\n" +
            "    }\n" +
            "\n" +
            "    private static class PostAnalyzeTaskListener implements TaskListener {\n" +
            "        @Override\n" +
            "        public void started(TaskEvent taskEvent) { \n" +
            "            if (taskEvent.getKind().equals(TaskEvent.Kind.COMPILATION)) {\n" +
            "                System.out.println(\"simpleplugin1 started for event \" + taskEvent.getKind());\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        @Override\n" +
            "        public void finished(TaskEvent taskEvent) {\n" +
            "            if (taskEvent.getKind().equals(TaskEvent.Kind.COMPILATION)) {\n" +
            "                System.out.println(\"simpleplugin1 finished for event \" + taskEvent.getKind());\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}";

    private static final String testClass = "class Test{}";

    void initialization(Path base) throws Exception {
        moduleSrc = base.resolve("plugin_mods_src");
        Path pluginMod1 = moduleSrc.resolve("pluginMod1");

        processorCompiledModules = base.resolve("mods");

        Files.createDirectories(processorCompiledModules);

        tb.writeJavaFiles(
                pluginMod1,
                pluginModule1,
                plugin1);

        String log = tb.new JavacTask()
                .options("-modulesourcepath", moduleSrc.toString())
                .outdir(processorCompiledModules)
                .files(findJavaFiles(moduleSrc))
                .run()
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.isEmpty()) {
            throw new AssertionError("Unexpected output: " + log);
        }

        classes = base.resolve("classes");
        Files.createDirectories(classes);
    }

    Path processorCompiledModules;
    Path moduleSrc;
    Path classes;

    @Test
    void testUseOnlyOneProcessor(Path base) throws Exception {
        initialization(base);
        String log = tb.new JavacTask()
                .options("-processormodulepath", processorCompiledModules.toString(),
                        "-Xplugin:simpleplugin1")
                .outdir(classes)
                .sources(testClass)
                .run()
                .writeAll()
                .getOutput(ToolBox.OutputKind.STDOUT);
        if (!log.trim().equals("simpleplugin1 started for event COMPILATION\n" +
                               "simpleplugin1 finished for event COMPILATION")) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }
}
