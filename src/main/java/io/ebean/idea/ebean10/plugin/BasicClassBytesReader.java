package io.ebean.idea.ebean10.plugin;

import io.ebean.enhance.common.ClassBytesReader;
import io.ebean.enhance.common.InputStreamTransform;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Just read the bytes using the ClassLoader.
 */
class BasicClassBytesReader implements ClassBytesReader {

	@Override
	public byte[] getClassBytes(String className, ClassLoader classLoader) {

		InputStream is = null;
		try {
			String resource = className.replace('.', '/') + ".class";

			// read the class bytes, and define the class
			URL url = classLoader.getResource(resource);
			if (url == null) {
				return null;
			}

			is = UrlHelper.openNoCache(url);
			return InputStreamTransform.readBytes(is);

		} catch (IOException e) {
			throw new RuntimeException("IOException reading bytes for " + className, e);

		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
