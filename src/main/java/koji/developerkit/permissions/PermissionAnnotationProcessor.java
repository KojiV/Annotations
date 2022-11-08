package koji.developerkit.permissions;

import com.google.auto.service.AutoService;
import com.google.common.collect.Maps;
import io.github.classgraph.AnnotationParameterValueList;
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

            ClassInfoList onesWithAnnotation = new ClassGraph()
                    .enableAnnotationInfo()
                    .enableClassInfo()
                    .scan()
                    .getClassesWithAnnotation(AddPermissions.class);

            onesWithAnnotation.forEach(t -> {
                AnnotationParameterValueList list = t.getAnnotationInfo(AddPermissions.class).getParameterValues();
                String prefix = (String) list.getValue("prefix");
                PermissionDefault permissionLevel = (PermissionDefault) list.getValue("permission");
                String description = (String) list.getValue("description");

                Map<String, Object> yml = Maps.newLinkedHashMap();
                Map<String, Map<String, Object>> permissionMetadata = Maps.newLinkedHashMap();
                t.getSubclasses().forEach(b ->
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
                    try (Writer w = file.openWriter()) {
                        String raw = yaml.dumpAs(yml, Tag.MAP, DumperOptions.FlowStyle.BLOCK);
                        w.write(raw);
                        w.flush();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
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
