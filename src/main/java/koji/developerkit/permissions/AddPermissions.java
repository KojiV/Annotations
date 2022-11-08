package koji.developerkit.permissions;

import org.bukkit.permissions.PermissionDefault;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface AddPermissions {
    PermissionDefault permission() default PermissionDefault.OP;
    String description() default "";
    String[] searchDirectories();
    String prefix();
}
