package koji.developerkit.permissions;

import com.google.auto.service.AutoService;
import com.google.common.collect.Maps;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import org.bukkit.permissions.PermissionDefault;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("koji.developerkit.permissions.AddPermissions")
@AutoService(Processor.class)
public class PermissionAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        try {
            FileObject file = processingEnv.getFiler().getResource(
                    StandardLocation.CLASS_OUTPUT, "", "plugin.yml"
            );

            annotations.forEach(a -> processAnnotation(a, roundEnvironment, file));

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    private void processAnnotation(TypeElement annotation, RoundEnvironment roundEnvironment, FileObject file) {
        roundEnvironment.getElementsAnnotatedWith(annotation).forEach(a -> {
            String prefix = a.getAnnotation(AddPermissions.class).prefix();
            PermissionDefault permissionLevel = a.getAnnotation(AddPermissions.class).permission();
            String description = a.getAnnotation(AddPermissions.class).description();

            ClassInfoList enchants = new ClassGraph()
                    .enableClassInfo()
                    .enableAnnotationInfo()
                    .scan()
                    .getClassInfo(a.getEnclosingElement().getSimpleName().toString())
                    .getSubclasses();

            Map<String, Object> yml = Maps.newLinkedHashMap();
            Map<String, Map<String, Object>> permissionMetadata = Maps.newLinkedHashMap();
            enchants.forEach(b ->
                permissionMetadata.put(
                        prefix + "." + b.getSimpleName().toLowerCase(),
                        processPermission(
                                description,
                                permissionLevel
                        )
                )
            );
            yml.put("permissions", permissionMetadata);
            try {
                Yaml yaml = new Yaml();
                try (Writer w = file.openWriter()){
                    String raw = yaml.dumpAs(yml, Tag.MAP, DumperOptions.FlowStyle.BLOCK);
                    w.write(raw);
                    w.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    protected Map<String, Object> processPermission(String description, PermissionDefault permissionDefault) {
        Map<String, Object> permission = Maps.newLinkedHashMap();

        if (!"".equals(description)) {
            permission.put("description", description);
        }

        if (PermissionDefault.OP != permissionDefault) {
            permission.put("default", permissionDefault.toString().toLowerCase());
        }

        return permission;
    }

    private AnnotationValue annotationValue(
            String value,
            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValuesWithDefaults
    ) {
        return elementValuesWithDefaults.keySet()
                .stream()
                .filter(b -> b.getSimpleName().toString().equals(value))
                .map(elementValuesWithDefaults::get).findAny().get();
    }

    public static String capitalize(String str) {
        return capitalize(str, null);
    }

    public static String capitalize(String str, char[] delimiters) {
        int delimLen = (delimiters == null ? -1 : delimiters.length);
        if (str == null || str.length() == 0 || delimLen == 0) {
            return str;
        }
        int strLen = str.length();
        StringBuilder buffer = new StringBuilder(strLen);
        boolean capitalizeNext = true;
        for (int i = 0; i < strLen; i++) {
            char ch = str.charAt(i);

            if (isDelimiter(ch, delimiters)) {
                buffer.append(ch);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                buffer.append(Character.toTitleCase(ch));
                capitalizeNext = false;
            } else {
                buffer.append(ch);
            }
        }
        return buffer.toString();
    }

    private static boolean isDelimiter(char ch, char[] delimiters) {
        if (delimiters == null) {
            return Character.isWhitespace(ch);
        }
        for (char delimiter : delimiters) {
            if (ch == delimiter) {
                return true;
            }
        }
        return false;
    }
}
