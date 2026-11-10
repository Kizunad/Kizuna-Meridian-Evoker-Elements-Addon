package dev.kizuna.meridian.evoker.tooling;

import com.google.protobuf.TextFormat;
import dev.kizuna.meridian.protocol.AddonProtobuf;
import dev.kizuna.meridian.protocol.pb.AddonMessages.AddonBundle;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 构建期把五脏炼化配方的文本源导出成 Framework 公共内容包，工具本身不进正式 JAR。 */
public final class ExportEvokerBundle {
    public static final String BUNDLE_RESOURCE = "META-INF/kizuna_meridian/addons/meridian_evoker.pb";
    private static final int SOURCE_ARGUMENT = 0;
    private static final int OUTPUT_ARGUMENT = 1;
    private static final int ARGUMENT_COUNT = 2;

    private ExportEvokerBundle() {
        // 只有 main 入口，不创建实例。
    }

    public static void main(String[] args) throws IOException {
        // 参数依次是文本源与输出目录；导出前先过 Framework 的包内校验。
        if (args.length != ARGUMENT_COUNT) {
            throw new IllegalArgumentException("用法：ExportEvokerBundle <文本源> <输出目录>");
        }
        AddonBundle bundle = parse(Path.of(args[SOURCE_ARGUMENT]));
        AddonProtobuf.validateBundle(bundle);
        Path target = Path.of(args[OUTPUT_ARGUMENT]).resolve(BUNDLE_RESOURCE);
        Files.createDirectories(target.getParent());
        Files.write(target, AddonProtobuf.encodeBundle(bundle));
    }

    private static AddonBundle parse(Path source) throws IOException {
        // 文本源按 Protobuf 文本格式整份合并，未知字段直接报错。
        AddonBundle.Builder builder = AddonBundle.newBuilder();
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            TextFormat.getParser().merge(reader, builder);
        }
        return builder.build();
    }
}
