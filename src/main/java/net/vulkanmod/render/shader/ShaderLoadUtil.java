package net.vulkanmod.render.shader;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.shaders.ShaderType;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import static net.vulkanmod.Initializer.LOGGER;

public abstract class ShaderLoadUtil {
    private static String activeShaderPack = "";
    private static final String RESOURCES_PATH = SPIRVUtils.class.getResource("/assets/vulkanmod").toExternalForm();

    public static final Set<String> REMAPPED_SHADERS = Sets.newHashSet("core/screenquad.vsh","core/rendertype_item_entity_translucent_cull.vsh");

    public static String getActiveShaderPack() {
        return activeShaderPack;
    }

    public static void setActiveShaderPack(String shaderPack) {
        activeShaderPack = shaderPack;
        Renderer.scheduleSwapChainUpdate();
    }

    public static List<String> getAvailableShaderPacks() {
        Path shaderpacksDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");

        if (!Files.exists(shaderpacksDir) || !Files.isDirectory(shaderpacksDir)) {
            return new ArrayList<>();
        }

        try (var stream = Files.list(shaderpacksDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toList();
        } catch (IOException e) {
            LOGGER.error("Error listing shaderpacks directory: {}", shaderpacksDir, e);
            return new ArrayList<>();
        }
    }

    public static void loadShaders(Pipeline.Builder pipelineBuilder, JsonObject config, String configName, String path) {
        String vertexShader = config.has("vertex") ? config.get("vertex").getAsString() : configName;
        String fragmentShader = config.has("fragment") ? config.get("fragment").getAsString() : configName;

        vertexShader = removeNameSpace(vertexShader);
        fragmentShader = removeNameSpace(fragmentShader);

        vertexShader = getFileName(vertexShader);
        fragmentShader = getFileName(fragmentShader);

        loadShader(pipelineBuilder, configName, path, vertexShader, SPIRVUtils.ShaderKind.VERTEX_SHADER);
        loadShader(pipelineBuilder, configName, path, fragmentShader, SPIRVUtils.ShaderKind.FRAGMENT_SHADER);
    }

    public static void loadShader(Pipeline.Builder pipelineBuilder, String configName, String path, SPIRVUtils.ShaderKind type) {
        String[] splitPath = splitPath(path);
        String shaderName = splitPath[1];
        String subPath = splitPath[0];

        loadShader(pipelineBuilder, configName, subPath, shaderName, type);
    }

    public static void loadShader(Pipeline.Builder pipelineBuilder, String configName, String path, String shaderName, SPIRVUtils.ShaderKind type) {
        String basePath = "%s/shaders/%s".formatted(RESOURCES_PATH, path);

        String source = getShaderSource(basePath, configName, shaderName, type);

        SPIRVUtils.SPIRV spirv = SPIRVUtils.compileShader(shaderName, source, type);

        switch (type) {
            case VERTEX_SHADER -> pipelineBuilder.setVertShaderSPIRV(spirv);
            case FRAGMENT_SHADER -> pipelineBuilder.setFragShaderSPIRV(spirv);
        }
    }

    public static String getConfigFilePath(String path, String rendertype) {
        String basePath = "%s/shaders/%s".formatted(RESOURCES_PATH, path);
        String configPath = "%s/%s/%s.json".formatted(basePath, rendertype, rendertype);

        Path filePath;
        try {
            filePath = FileSystems.getDefault().getPath(configPath);

            if (!Files.exists(filePath)) {
                configPath = "%s/%s.json".formatted(basePath, rendertype);
                filePath = FileSystems.getDefault().getPath(configPath);
            }

            if (!Files.exists(filePath)) {
                return null;
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        return filePath.toString();
    }

    public static JsonObject getJsonConfig(String path, String rendertype) {
        // Check for external shader
        if (rendertype.contains(String.valueOf(ResourceLocation.NAMESPACE_SEPARATOR))) {
            JsonObject loadedData = loadShaderPackJson(ResourceLocation.parse(path), path);

            if (loadedData != null) {
                return loadedData;
            }

            LOGGER.warn("Shader pack JSON not found for path: {}", path);
            return null;
        }

        String basePath = "%s/shaders/%s".formatted(RESOURCES_PATH, path);
        String configPath = "%s/%s/%s.json".formatted(basePath, rendertype, rendertype);

        InputStream stream;
        try {
            stream = getInputStream(configPath);

            if (stream == null) {
                configPath = "%s/%s.json".formatted(basePath, rendertype);
                stream = getInputStream(configPath);
            }

            if (stream == null) {
                return null;
            }

            JsonElement jsonElement = JsonParser.parseReader(new BufferedReader(new InputStreamReader(stream)));
            stream.close();

            return (JsonObject) jsonElement;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

    }

    public static String getShaderSource(ResourceLocation resourceLocation, ShaderType type) {
        String shaderExtension = switch (type) {
            case VERTEX -> ".vsh";
            case FRAGMENT -> ".fsh";
        };

        String path = resourceLocation.getPath();
        String[] splitPath = splitPath(path);
        String shaderName = "%s%s".formatted(splitPath[1], shaderExtension);
        String shaderFile = "%s/shaders/%s/%s".formatted(RESOURCES_PATH, path, shaderName);

        InputStream stream;
        try {
            stream = getInputStream(shaderFile);

            if (stream == null) {
                return null;
            }

            String source = IOUtils.toString(new BufferedReader(new InputStreamReader(stream)));
            stream.close();

            return source;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static String getShaderSource(String path, ShaderType type) {
        String shaderExtension = switch (type) {
            case VERTEX -> ".vsh";
            case FRAGMENT -> ".fsh";
        };

        String[] splitPath = splitPath(path);
        String shaderName = "%s%s".formatted(splitPath[1], shaderExtension);

        String shaderFile = "%s/shaders/%s/%s".formatted(RESOURCES_PATH, path, shaderName);

        InputStream stream;
        try {
            stream = getInputStream(shaderFile);
            String source = IOUtils.toString(new BufferedReader(new InputStreamReader(stream)));
            stream.close();

            return source;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonObject loadShaderPackJson(ResourceLocation shaderPack, String renderType) {
        Path shaderpacksDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks");

        if (!Files.exists(shaderpacksDir) || !Files.isDirectory(shaderpacksDir)) {
        LOGGER.warn("Shaderpacks directory not found at: {}", shaderpacksDir);
            return null;
        }

        File[] shaderpackFiles = shaderpacksDir.toFile().listFiles();

        if (shaderpackFiles == null) {
            LOGGER.warn("Could not list files in shaderpacks directory.");
            return null;
        }

        for (File shaderpackFile : shaderpackFiles) {
            String packName = shaderpackFile.getName();

            try {
                if (shaderpackFile.isDirectory()) {
                    String jsonFileName = "%s/shaders/%s/%s.json".formatted(packName, renderType, renderType);
                    Path jsonFilePath = shaderpackFile.toPath().resolve(jsonFileName);

                    if (Files.exists(jsonFilePath)) {
                        try (InputStream stream = Files.newInputStream(jsonFilePath)) {
                            JsonElement jsonElement = JsonParser.parseReader(new BufferedReader(new InputStreamReader(stream)));
                            return (JsonObject) jsonElement;
                        } catch (IOException e) {
                            LOGGER.error("I/O error while reading shaderpack JSON: {}", jsonFileName, e);
                        }
                    } else {
                        LOGGER.warn("Shaderpack JSON file not found: {}", jsonFileName);
                    }
                } else if (shaderpackFile.getName().toLowerCase().endsWith(".zip")) {
                    // Handle .zip archive shaderpacks
                    String jsonFileName = "shaders/%s/%s.json".formatted(renderType, renderType);
                    try (FileSystem zipFileSystem = FileSystems.newFileSystem(shaderpackFile.toPath(), (ClassLoader) null)) {
                        Path jsonFilePath = zipFileSystem.getPath(jsonFileName);

                        if (Files.exists(jsonFilePath)) {
                            try (InputStream stream = Files.newInputStream(jsonFilePath)) {
                                JsonElement jsonElement = JsonParser.parseReader(new BufferedReader(new InputStreamReader(stream)));
                                return (JsonObject) jsonElement;
                            } catch (IOException e) {
                                LOGGER.error("I/O error while reading shaderpack JSON from zip: {}", jsonFileName, e);
                            }
                        } else {
                            LOGGER.warn("Shaderpack JSON file not found in zip: {}", jsonFileName);
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error opening shaderpack zip file: {}", packName, e);
                    }
                }
            } catch (JsonSyntaxException e) {
                LOGGER.error("JSON syntax error in shaderpack: {}", packName, e);
            } catch (ClassCastException e) {
                LOGGER.error("Error casting JSON element in shaderpack: {}", packName, e);
            } catch (Exception e) {
                LOGGER.error("Unexpected error while processing shaderpack: {}", packName, e);
            }
        }

        LOGGER.warn("No valid shaderpack JSON found for render type: {}", renderType);
        return null;
    }

    public static String getShaderSource(String basePath, String rendertype, String shaderName, SPIRVUtils.ShaderKind type) {
        String shaderExtension = switch (type) {
            case VERTEX_SHADER -> ".vsh";
            case FRAGMENT_SHADER -> ".fsh";
            default -> throw new UnsupportedOperationException("shader type %s unsupported");
        };

        String shaderPath = "/%s/%s".formatted(rendertype, rendertype);
        String shaderFile = "%s%s%s".formatted(basePath, shaderPath, shaderExtension);

        InputStream stream;
        try {
            stream = getInputStream(shaderFile);

            if (stream == null) {
                shaderPath = "/%s/%s".formatted(rendertype, shaderName);
                shaderFile = "%s%s%s".formatted(basePath, shaderPath, shaderExtension);
                stream = getInputStream(shaderFile);
            }

            if (stream == null) {
                shaderPath = "/%s/%s".formatted(shaderName, shaderName);
                shaderFile = "%s%s%s".formatted(basePath, shaderPath, shaderExtension);
                stream = getInputStream(shaderFile);
            }

            if (stream == null) {
                return null;
            }

            String source = IOUtils.toString(new BufferedReader(new InputStreamReader(stream)));
            stream.close();

            return source;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx > -1 ? path.substring(idx + 1) : path;
    }

    public static String removeNameSpace(String path) {
        int idx = path.indexOf(':');
        return idx > -1 ? path.substring(idx + 1) : path;
    }

    public static String[] splitPath(String path) {
        int idx = path.lastIndexOf('/');

        return new String[] {path.substring(0, idx), path.substring(idx + 1)};
    }

    public static InputStream getInputStream(String path) {
        try {
            var path1 = Paths.get(new URI(path));

            if (!Files.exists(path1))
                return null;

            return Files.newInputStream(path1);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
