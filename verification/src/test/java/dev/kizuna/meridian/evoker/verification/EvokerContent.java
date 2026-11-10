package dev.kizuna.meridian.evoker.verification;

import dev.kizuna.meridian.human.server.HumanContent;
import dev.kizuna.meridian.protocol.AddonProtobuf;
import dev.kizuna.meridian.protocol.MeridianProtocol;
import dev.kizuna.meridian.protocol.pb.AddonMessages.AddonBundle;
import dev.kizuna.meridian.protocol.pb.AddonMessages.AddonHostProfile;
import dev.kizuna.meridian.protocol.pb.AddonMessages.AddonRegistration;
import dev.kizuna.meridian.server.AddonRegistry;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueCatalog;
import dev.kizuna.meridian.technique.server.TechniqueCatalogManifest;
import dev.kizuna.meridian.technique.server.TechniqueRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/** 读取五脏诀目录与炼化配方包，并以真实 Human 内容完成注册验证。 */
public final class EvokerContent {
    public static final String NAMESPACE = "meridian_evoker";
    public static final String CATALOG_RESOURCE = "/data/meridian_evoker/technique_catalog.txtpb";
    public static final String BUNDLE_RESOURCE = "/META-INF/kizuna_meridian/addons/meridian_evoker.pb";
    public static final String RECIPE_FAMILY = "meridian_evoker:refine";
    private static final String FABRIC_RESOURCES = "evoker.fabricResources";

    private EvokerContent() {
        // 只提供静态验证入口。
    }

    public static TechniqueCatalog catalog() throws IOException {
        // 单元验证与正式发现共用功法库的有界清单解析器。
        try (InputStream stream = open(CATALOG_RESOURCE)) {
            byte[] bytes = stream.readNBytes(TechniqueCatalogManifest.MAX_BYTES + 1);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return TechniqueCatalogManifest.parseText(NAMESPACE + ":" + CATALOG_RESOURCE.substring(1), text);
        }
    }

    public static AddonBundle bundle() throws IOException {
        // 读构建导出的二进制内容包，与宿主运行时读到的是同一份字节。
        try (InputStream stream = open(BUNDLE_RESOURCE)) {
            byte[] bytes = stream.readNBytes(MeridianProtocol.MAX_PAYLOAD_BYTES + 1);
            return AddonProtobuf.decodeBundle(bytes);
        }
    }

    public static TechniqueRegistry registry() throws IOException {
        // Human 模板标签与本仓配方包一起登记，再交给功法库校验目录。
        return TechniqueRegistry.load(addons(), List.of(catalog()));
    }

    public static AddonRegistry addons() throws IOException {
        // 测试宿主声明 Human 内容要求的完整 Framework 能力集合；本仓配方包依赖 human 修订 1。
        AddonHostProfile host = AddonHostProfile.newBuilder()
                .setHostId("meridian:java_reference")
                .setApiVersion(AddonProtobuf.API_VERSION)
                .addCapabilities(AddonRegistry.BODY_TEMPLATES)
                .addCapabilities(AddonRegistry.ORGAN_RECIPES)
                .addCapabilities(AddonRegistry.ORGAN_INTAKE)
                .addCapabilities(AddonRegistry.ACTIONS)
                .addCapabilities(AddonRegistry.TISSUE_RECOVERY)
                .addCapabilities(AddonRegistry.PASSIVE_QI_FLOW)
                .addCapabilities(AddonRegistry.OPENING_DEFINITIONS)
                .addCapabilities(AddonRegistry.OPENING_RUNTIME)
                .addCapabilities(AddonRegistry.ORGAN_ENDURANCE)
                .build();
        AddonRegistration registration = AddonRegistration.newBuilder()
                .setProtocolVersion(MeridianProtocol.VERSION)
                .setHost(host)
                .addBundles(HumanContent.bundle())
                .addBundles(bundle())
                .build();
        return AddonRegistry.load(registration);
    }

    public static Path fabricResources() {
        // 构建脚本传入 fabric 模块资源根目录；缺少时说明不是经 Gradle 运行。
        String directory = System.getProperty(FABRIC_RESOURCES);
        if (directory == null) {
            throw new IllegalStateException("缺少系统属性 " + FABRIC_RESOURCES);
        }
        return Path.of(directory);
    }

    private static InputStream open(String resource) throws IOException {
        // 资源缺失是打包错误，明确报出路径。
        InputStream stream = EvokerContent.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("找不到五脏诀资源：" + resource);
        }
        return stream;
    }
}
