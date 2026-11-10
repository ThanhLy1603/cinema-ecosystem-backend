package com.cinema.modules.movie.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MovieControllerAuthorizationTest {

    private static final String STAFF = "hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')";
    private static final String ADMIN_ONLY = "hasAuthority('ROLE_ADMIN')";

    private static PreAuthorize methodAuthorization(Class<?> controllerClass, String methodName) {
        Method method = Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(declaredMethod -> declaredMethod.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        return method.getAnnotation(PreAuthorize.class);
    }

    @Test
    void controllersAreOpenToStaffOnly() {
        for (Class<?> controllerClass : new Class<?>[] {
                AdminMovieController.class, AdminGenreController.class, AdminPersonController.class}) {
            PreAuthorize preAuthorize = controllerClass.getAnnotation(PreAuthorize.class);
            assertNotNull(preAuthorize);
            assertEquals(STAFF, preAuthorize.value());
        }
    }

    @Test
    void managersCanCreateAndUpdateCatalogEntries() {
        assertNull(methodAuthorization(AdminMovieController.class, "createMovie"));
        assertNull(methodAuthorization(AdminMovieController.class, "updateMovie"));
        assertNull(methodAuthorization(AdminGenreController.class, "createGenre"));
        assertNull(methodAuthorization(AdminGenreController.class, "updateGenre"));
        assertNull(methodAuthorization(AdminPersonController.class, "createPerson"));
        assertNull(methodAuthorization(AdminPersonController.class, "updatePerson"));
    }

    @Test
    void deletingAndRestoringStayAdminOnly() {
        assertEquals(ADMIN_ONLY, methodAuthorization(AdminMovieController.class, "deleteMovie").value());
        assertEquals(ADMIN_ONLY, methodAuthorization(AdminMovieController.class, "restoreMovie").value());
        assertEquals(ADMIN_ONLY, methodAuthorization(AdminGenreController.class, "deleteGenre").value());
        assertEquals(ADMIN_ONLY, methodAuthorization(AdminPersonController.class, "deletePerson").value());
    }
}
