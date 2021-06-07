package org.hl7.tinkar.common.service;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

/**

 public enum PathServiceGetter implements ServiceGetter<PathService> {
 INSTANCE;

 PathService service;

 PathServiceGetter() {
 this.service = get(PathService.class);
 }

 @Override
 public PathService get() {
 return service;
 }
 }


 * @param <T>
 */

public interface ServiceGetter<T> {

    default <T> T get(Class<T> clazz) {
        ServiceLoader<T> serviceLoader = ServiceLoader.load(clazz);
        Optional<T> optionalService = serviceLoader.findFirst();
        if (optionalService.isPresent()) {
            return optionalService.get();
        } else {
            throw new NoSuchElementException("No " + clazz.getName() +
                    " found by ServiceLoader...");
        }
    }

    T get();

}
