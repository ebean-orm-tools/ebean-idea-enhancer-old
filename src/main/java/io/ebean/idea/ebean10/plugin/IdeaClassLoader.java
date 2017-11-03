package io.ebean.idea.ebean10.plugin;


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
  private final ConcurrentHashMap<String, Class<?>> classCache = new ConcurrentHashMap<>();

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
      return classCache.computeIfAbsent(name, this::readClass);
    }
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    return super.getResources(name);
  }

  private Class<?> readClass(String className) {
    byte[] bytes = bytesReader.getClassBytes(className.replace('.', '/'), parent);
    if (bytes == null) {
      // return null instead of exception - prevent errors in IntelliJ
      return null;
    }
    return defineClass(className, bytes, 0, bytes.length);
  }
}