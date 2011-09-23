package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Getter;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Getter lombok annotation on a field
 * Creates getter method for this field
 *
 * @author Plushnikov Michail
 */
public class GetterFieldProcessor extends AbstractLombokFieldProcessor {

  public static final String CLASS_NAME = Getter.class.getName();

  public GetterFieldProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodVisibility(psiAnnotation);
    if (methodVisibility != null) {
      target.add((Psi) createGetterMethod(psiField, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;

    final String methodVisibity = LombokProcessorUtil.getMethodVisibility(psiAnnotation);
    result = null != methodVisibity;

    final String lazyAsString = PsiAnnotationUtil.getAnnotationValue(psiAnnotation, "lazy");
    final Boolean lazy = Boolean.valueOf(lazyAsString);
    if (null == methodVisibity && lazy) {
      builder.addWarning("'lazy' does not work with AccessLevel.NONE.");
    }

    if (result && lazy) {
      if (!psiField.hasModifierProperty(PsiModifier.FINAL) || !psiField.hasModifierProperty(PsiModifier.PRIVATE)) {
        builder.addError("'lazy' requires the field to be private and final");
      }
      if (null == psiField.getInitializer()) {
        builder.addError("'lazy' requires field initialization.");
      }
    }

    if (result) {
      result = validateExistingMethods(psiField, builder);
    }

    return result;
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final Collection<String> methodNames = TransformationsUtil.toAllGetterNames(psiField.getName(), isBoolean);
      final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasMethodByName(classMethods, methodName)) {
          final String setterMethodName = TransformationsUtil.toGetterName(psiField.getName(), isBoolean);

          builder.addWarning(String.format("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName));
          result = false;
        }
      }
    }
    return result;
  }

  @NotNull
  public PsiMethod createGetterMethod(@NotNull PsiField psiField, @NotNull String methodVisibility) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      final String fieldName = psiField.getName();
      final PsiType psiReturnType = psiField.getType();
      String methodName = TransformationsUtil.toGetterName(fieldName, PsiType.BOOLEAN.equals(psiReturnType));

      final Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(psiField);
      final String annotationsString = PsiAnnotationUtil.buildAnnotationsString(annotationsToCopy);

      builder.append(methodVisibility);
      if (StringUtil.isNotEmpty(methodVisibility)) {
        builder.append(' ');
      }
      if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
        builder.append(PsiModifier.STATIC).append(' ');
      }
      builder.append(annotationsString);
      builder.append(psiReturnType.getCanonicalText());
      builder.append(' ');
      builder.append(methodName);
      builder.append("()");
      builder.append("{ return this.").append(fieldName).append("; }");

      PsiClass psiClass = psiField.getContainingClass();
      assert psiClass != null;

      UserMapKeys.addReadUsageFor(psiField);

      return PsiMethodUtil.createMethod(psiClass, builder.toString(), psiField);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


}