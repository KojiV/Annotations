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
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("koji.developerkit.permissions.AddPermissions")
@AutoService(Processor.class)
@SuppressWarnings("unused")
public class PermissionAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        try {
            FileObject file = processingEnv.getFiler().getResource(
                    StandardLocation.CLASS_OUTPUT, "", "plugin.yml"
            );
            String msg = message(file);
            file.delete();

            file = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT, "", "plugin.yml"
            );

            Map<String, Object> yml = Maps.newLinkedHashMap();
            Map<String, Map<String, Object>> permissionMetadata = Maps.newLinkedHashMap();
            annotations.forEach(a -> permissionMetadata.putAll(processAnnotation(a, roundEnvironment)));
            yml.put("permission", permissionMetadata);

            try {
                Yaml yaml = new Yaml();
                try (Writer w = file.openWriter()) {
                    w.append(msg).append("\n");
                    w.append(yml.get("permission").toString());
                    String raw = yaml.dumpAs(yml, Tag.MAP, DumperOptions.FlowStyle.BLOCK);
                    w.write(raw);
                    w.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    private String message(FileObject obj) {
        try {
            BufferedInputStream inputStreamReader = new BufferedInputStream(obj.openInputStream());
            String content = "";
            while (inputStreamReader.available() > 0) {
                char c = (char) inputStreamReader.read();
                content = content.concat(String.valueOf(c));
            }
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private Map<String, Map<String, Object>> processAnnotation(TypeElement annotation, RoundEnvironment roundEnvironment) {
        Map<String, Map<String, Object>> permissionMetadata = Maps.newLinkedHashMap();

        roundEnvironment.getElementsAnnotatedWith(annotation).forEach(a -> {
            String prefix = a.getAnnotation(AddPermissions.class).prefix();
            PermissionDefault permissionLevel = a.getAnnotation(AddPermissions.class).permission();
            String description = a.getAnnotation(AddPermissions.class).description();

            ClassInfoList enchants = new ClassGraph()
                    .enableClassInfo()
                    .scan()
                    .getSubclasses(a.asType().toString());

            enchants.forEach(b ->
                permissionMetadata.put(
                        prefix + "." + b.getSimpleName().toLowerCase(),
                        processPermission(
                                description,
                                permissionLevel
                        )
                )
            );
        });
        return permissionMetadata;
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
}
