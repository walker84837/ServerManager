package org.winlogon.servermanager;

public class ExceptionWrappers {
    /**
     * Wraps a boolean supplier in a try-catch block and returns false in case of an exception.
     * @param supplier The supplier to wrap.
     * @return The result of the supplier or false if an exception was thrown.
     */
    public static boolean safe(CheckedSupplier<Boolean> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return false;
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
