package org.avaje.idea.ebean10.plugin;


import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class loader used during enhancement.
 */
public final class IdeaClassLoader extends ClassLoader {

  /**
   * The implementation for loading raw byte code.
   */
  private final IdeaClassBytesReader bytesReader;

  /**
   * Local cache of defined classes.
   */
  private final ConcurrentHashMap<String, Class<?>> definedClasses = new ConcurrentHashMap<>();

  private final ClassLoader parent;

  /**
   * Construct with a parent classLoader and raw bytecodeLoader.
   */
  public IdeaClassLoader(ClassLoader parent, IdeaClassBytesReader bytesReader) {
    super(parent);
    this.parent = parent;
    this.bytesReader = bytesReader;
  }

  @Override
  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    try {
      return super.findClass(name);

    } catch (ClassNotFoundException e) {

      synchronized (this) {
        Class<?> aClass = definedClasses.get(name);
        if (aClass != null) {
          // return cache hit
          return aClass;
        }

        // load the raw bytes
        byte[] bytes = bytesReader.getClassBytes(name.replace('.', '/'), parent);
        if (bytes == null) {
          // return null instead of exception - prevent errors in IntelliJ
          return null;
//          throw new ClassNotFoundException(name);
        }

        // define and cache
        aClass = defineClass(name, bytes, 0, bytes.length);
        definedClasses.put(name, aClass);
        return aClass;
      }
    }
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    return super.getResources(name);
  }
}