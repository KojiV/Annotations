package koji.developerkit.events;

import com.google.auto.service.AutoService;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import org.reflections.Reflections;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("koji.developerkit.events.ExpandEventHandler")
@AutoService(Processor.class)
@SuppressWarnings("unused")
public class EventAnnotationProcessor extends AbstractProcessor {

    private TreeMaker maker;
    private Trees trees;
    private Names names;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        JavacProcessingEnvironment javacProcessingEnvironment = (JavacProcessingEnvironment) this.processingEnv;
        Context context = javacProcessingEnvironment.getContext();

        maker = TreeMaker.instance(context);
        trees = Trees.instance(this.processingEnv);
        names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        annotations.forEach(a ->
            processAnnotation(a, roundEnvironment)
        );

        return true;
    }

    @SuppressWarnings("unchecked")
    private void processAnnotation(TypeElement annotation, RoundEnvironment roundEnvironment) {
        roundEnvironment.getElementsAnnotatedWith(annotation).forEach(a -> {
            boolean includeDeprecated = a.getAnnotation(ExpandEventHandler.class).includeDeprecated();

            AnnotationMirror mirror = a.getAnnotationMirrors().stream().filter(b ->
                    b.getAnnotationType().toString().equals(
                            ExpandEventHandler.class.getName()
                    )).findAny().get();
            Map<? extends ExecutableElement, ? extends AnnotationValue> elementValuesWithDefaults = processingEnv.getElementUtils().getElementValuesWithDefaults(mirror);


            AnnotationValue value = elementValuesWithDefaults.keySet()
                    .stream()
                    .filter(b -> b.getSimpleName().toString().equals("exclude"))
                    .map(elementValuesWithDefaults::get).findAny().get();

            // OH god the crap
            Set<String> ignoredClassNames = ((List<com.sun.tools.javac.code.Attribute.Class>) value.getValue())
                    .stream()
                    .map(Attribute.Class::toString)
                    .map(b -> {
                        int last = b.lastIndexOf(".");
                        return b.substring(0, last);
                    }).collect(Collectors.toSet());


            JCTree.JCMethodDecl toExecute = (JCTree.JCMethodDecl) trees.getTree(a);

            JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) trees.getTree(a.getEnclosingElement());

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, String.format("Generating event handlers for %s", classDecl.getSimpleName().toString()));
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Ignoring events: " + ignoredClassNames);

            JCTree.JCExpression voidExpression = maker.TypeIdent(TypeTag.VOID);

            JCTree.JCExpression annotationName = dotNames("org.bukkit.event.EventHandler");
            JCTree.JCAnnotation eventHandlerAnnotation = maker.Annotation(annotationName, List.nil());

            final JCTree.JCModifiers modifiers = maker.Modifiers(Modifier.PUBLIC, List.of(eventHandlerAnnotation));

            final Name eventName = names.fromString("event");

            TypeMirror type = ((ExecutableType) a.asType()).getParameterTypes().get(0);

            Reflections reflections = new Reflections();
            try {
                Stream<Class<?>> events =
                        reflections.getSubTypesOf((Class<Object>) Class.forName(type.toString()))
                                .stream()
                                .filter(info -> !ignoredClassNames.contains(info.getName()))
                                .filter(info -> Modifier.isAbstract(info.getModifiers()));

                if (!includeDeprecated) {
                    events = events.filter(info -> !info.isAnnotationPresent(Deprecated.class));
                }


                java.util.List<JCTree.JCMethodDecl> added = events.map(b -> {
                    JCTree.JCVariableDecl variableDecl = maker.VarDef(
                            maker.Modifiers(Flags.PARAMETER),
                            eventName,
                            dotNames(b.getName()),
                            null
                    );

                    variableDecl.setPos(Integer.MAX_VALUE);

                    final JCTree.JCMethodInvocation methoddec = maker.Apply(
                            List.nil(),
                            maker.Ident(toExecute.getName()),
                            List.of(maker.Ident(eventName))
                    );
                    JCTree.JCStatement jcStatement = maker.Exec(methoddec);
                    JCTree.JCBlock block = maker.Block(0, List.of(jcStatement));

                    return maker.MethodDef(modifiers,
                            names.fromString("on" + b.getSimpleName()),
                            voidExpression,
                            List.nil(),
                            List.of(variableDecl), //params
                            List.nil(),
                            block,
                            null);

                }).collect(Collectors.toList());

                List<JCTree> list = classDecl.defs;

                java.util.List<JCTree> newList = new ArrayList<>(list);
                newList.addAll(added);

                classDecl.defs = List.from(newList);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        });
    }


    private JCTree.JCExpression dotNames(String dotted) {
        String[] parts = dotted.split("\\.");

        JCTree.JCExpression result = maker.Ident(names.fromString(parts[0]));

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];

            result = maker.Select(result, names.fromString(part));
        }

        return result;
    }
}
