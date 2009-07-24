package com.intellij.tapestry.core;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.tapestry.core.events.TapestryEventsManager;
import com.intellij.tapestry.core.java.IJavaClassType;
import com.intellij.tapestry.core.java.IJavaTypeCreator;
import com.intellij.tapestry.core.java.IJavaTypeFinder;
import com.intellij.tapestry.core.model.Library;
import com.intellij.tapestry.core.model.presentation.Component;
import com.intellij.tapestry.core.model.presentation.Page;
import com.intellij.tapestry.core.model.presentation.PresentationLibraryElement;
import com.intellij.tapestry.core.resource.IResource;
import com.intellij.tapestry.core.resource.IResourceFinder;
import com.intellij.tapestry.core.util.LocalizationUtils;
import static com.intellij.tapestry.core.util.StringUtils.isNotEmpty;
import com.intellij.tapestry.intellij.facet.TapestryFacet;
import com.intellij.tapestry.intellij.facet.TapestryFacetConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * A Tapestry project. Every IDE implementation must hold a reference to an instance of this class for each project.
 */
public class TapestryProject {

  public static final Object[] JAVA_STRUCTURE_DEPENDENCY = {PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT};
  public static final Object[] OUT_OF_CODE_BLOCK_DEPENDENCY = {PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT};
  /**
   * The application library id.
   */
  public static final String APPLICATION_LIBRARY_ID = "application";
  /**
   * The Tapestry core library id.
   */
  public static final String CORE_LIBRARY_ID = "core";

  private final Library myCoreLibrary = new Library(CORE_LIBRARY_ID, TapestryConstants.CORE_LIBRARY_PACKAGE, this);
  private final Module myModule;
  private final IResourceFinder myResourceFinder;
  private Collection<Library> myCachedLibraries;
  private String myLastApplicationPackage;

  private final IJavaTypeFinder myJavaTypeFinder;
  private final IJavaTypeCreator myJavaTypeCreator;
  private final TapestryEventsManager myEventsManager;


  public TapestryProject(@NotNull Module module,
                         @NotNull IResourceFinder resourceFinder,
                         @NotNull IJavaTypeFinder javaTypeFinder,
                         @NotNull IJavaTypeCreator javaTypeCreator) {
    myModule = module;
    myResourceFinder = resourceFinder;
    myJavaTypeFinder = javaTypeFinder;
    myJavaTypeCreator = javaTypeCreator;

    myEventsManager = new TapestryEventsManager();
    myLastApplicationPackage = null;
  }

  /**
   * @return the application root package.
   */
  @Nullable
  public String getApplicationRootPackage() {
    TapestryFacetConfiguration myConfiguration = TapestryFacet.findFacetConfiguration(myModule);
    return myConfiguration == null ? null : myConfiguration.getApplicationPackage();
  }

  /**
   * @return the application pages root package.
   */
  @Nullable
  public String getPagesRootPackage() {
    return getElementsRootPackage(TapestryConstants.PAGES_PACKAGE);
  }

  /**
   * @return the application components root package.
   */
  @Nullable
  public String getComponentsRootPackage() {
    return getElementsRootPackage(TapestryConstants.COMPONENTS_PACKAGE);
  }

  /**
   * @return the application mixins root package.
   */
  @Nullable
  public String getMixinsRootPackage() {
    return getElementsRootPackage(TapestryConstants.MIXINS_PACKAGE);
  }

  @Nullable
  private String getElementsRootPackage(@NotNull String subpackage) {
    final String rootPackage = getApplicationRootPackage();
    if(rootPackage == null) return null;
    return rootPackage + "." + subpackage;
  }
  /**
   * Finds the available libraries of this project.
   *
   * @return a collection of all the available libraries to this project.
   */
  @NotNull
  public Collection<Library> getLibraries() {

    String applicationRootPackage = getApplicationRootPackage();
    if (applicationRootPackage == null) return Collections.emptyList();
    if (myCachedLibraries != null && isNotEmpty(myLastApplicationPackage)) {
      if (myLastApplicationPackage.equals(applicationRootPackage)) {
        return myCachedLibraries;
      }
    }

    myCachedLibraries = new ArrayList<Library>();
    myLastApplicationPackage = applicationRootPackage;

    myCachedLibraries.add(new Library(APPLICATION_LIBRARY_ID, applicationRootPackage, this));
    myCachedLibraries.add(myCoreLibrary);

    return myCachedLibraries;
  }

  /**
   * Finds the application library.
   *
   * @return the application library.
   */
  @Nullable
  public Library getApplicationLibrary() {
    Collection<Library> libraries = getLibraries();
    return libraries.size() == 0 ? null : libraries.iterator().next();
  }

  /**
   * Finds a page by name in the Tapestry application.
   *
   * @param pageName the page name to look.
   * @return the page with the given name, or <code>null</code> if the page isn't found.
   */
  @Nullable
  public Page findPage(String pageName) {
    return (Page)ourNameToPageMap.get(myModule).get(pageName.toLowerCase());
  }

  @NotNull
  public String[] getAvailablePageNames() {
    final Set<String> names = ourNameToPageMap.get(myModule).keySet();
    return names.toArray(new String[names.size()]);
  }

  private static final ElementsCachedMap ourNameToPageMap = new ElementsCachedMap("ourFqnToComponentMap", false, true) {
    protected String computeKey(PresentationLibraryElement element) {
      return element.getName().toLowerCase().replace('/', '.');
    }
  };


  /**
   * Finds a page by class in the Tapestry application.
   *
   * @param pageClass the page class to look.
   * @return the page of the given class, or <code>null</code> if the page isn't found.
   */
  @Nullable
  public Page findPage(@NotNull IJavaClassType pageClass) {
    return (Page)ourFqnToPageMap.get(myModule).get(pageClass.getFullyQualifiedName());
  }

  private static final ElementsCachedMap ourFqnToPageMap = new ElementsCachedMap("ourFqnToPageMap", false, true) {
    protected String computeKey(PresentationLibraryElement element) {
      return element.getElementClass().getFullyQualifiedName();
    }
  };

  /**
   * Finds a component by name in the Tapestry application.
   *
   * @param componentName the component name to look.
   * @return the component with the given name, or <code>null</code> if the component isn't found.
   */
  @Nullable
  public Component findComponent(@NotNull String componentName) {
    return (Component)ourNameToComponentMap.get(myModule).get(componentName.toLowerCase());
  }

  @NotNull
  public String[] getAvailableComponentNames() {
    final Set<String> names = ourNameToComponentMap.get(myModule).keySet();
    return names.toArray(new String[names.size()]);
  }

  private static final ElementsCachedMap ourNameToComponentMap = new ElementsCachedMap("ourFqnToComponentMap", true, false) {
    protected String computeKey(PresentationLibraryElement element) {
      return element.getName().toLowerCase().replace('/', '.');
    }
  };

  /**
   * Finds a Tapestry element, either a component or page can be returned.
   *
   * @param elementClass the element class to find.
   * @return either the page or component to which the given class belongs to, or <code>null</code> if the element isn't found.
   */
  @Nullable
  public PresentationLibraryElement findElement(@NotNull IJavaClassType elementClass) {
    Component component = findComponent(elementClass);
    return component != null ? component : findPage(elementClass);
  }

  /**
   * Finds a component by class in the Tapestry application.
   *
   * @param componentClass the component class to look.
   * @return the component of the given class, or <code>null</code> if the component isn't found.
   */
  @Nullable
  public Component findComponent(@NotNull IJavaClassType componentClass) {
    return (Component)ourFqnToComponentMap.get(myModule).get(componentClass.getFullyQualifiedName());
  }

  private static final ElementsCachedMap ourFqnToComponentMap = new ElementsCachedMap("ourFqnToComponentMap", true, false) {
    protected String computeKey(PresentationLibraryElement element) {
      return element.getElementClass().getFullyQualifiedName();
    }
  };

  /**
   * Finds the component class from it's template.
   *
   * @param template the component template.
   * @return the component class.
   */
  @Nullable
  public PresentationLibraryElement findElementByTemplate(@NotNull PsiFile template) {
    String templatePath = new File(template.getOriginalFile().getViewProvider().getVirtualFile().getPath()).getAbsolutePath();
    return ourTemplateToElementMap.get(myModule).get(LocalizationUtils.unlocalizeFileName(templatePath));
  }

  private static final ElementsCachedMap ourTemplateToElementMap = new ElementsCachedMap("ourTemplateToElementMap", true, true) {
    @Nullable
    protected String computeKey(PresentationLibraryElement element) {
      final IResource[] resources = element.getTemplate();
      return resources.length > 0 ? LocalizationUtils.unlocalizeFileName(resources[0].getFile().getAbsolutePath()) : null;
    }
  };

  @NotNull
  public Collection<PresentationLibraryElement> getAvailableElements() {
    return ourFqnToComponentMap.get(myModule).values();
  }

  @NotNull
  public IJavaTypeFinder getJavaTypeFinder() {
    return myJavaTypeFinder;
  }

  @NotNull
  public IJavaTypeCreator getJavaTypeCreator() {
    return myJavaTypeCreator;
  }

  public IResourceFinder getResourceFinder() {
    return myResourceFinder;
  }

  @NotNull
  public TapestryEventsManager getEventsManager() {
    return myEventsManager;
  }

}
