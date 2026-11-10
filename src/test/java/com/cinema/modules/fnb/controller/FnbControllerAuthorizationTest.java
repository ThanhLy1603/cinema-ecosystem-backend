package com.cinema.modules.fnb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FnbControllerAuthorizationTest {

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
    void adminControllersAreOpenToStaffOnly() {
        for (Class<?> controllerClass : new Class<?>[] {AdminFnbItemController.class, AdminFnbComboController.class}) {
            PreAuthorize preAuthorize = controllerClass.getAnnotation(PreAuthorize.class);
            assertNotNull(preAuthorize);
            assertEquals(STAFF, preAuthorize.value());
        }
    }

    @Test
    void managersCanCreateAndUpdate() {
        assertNull(methodAuthorization(AdminFnbItemController.class, "createItem"));
        assertNull(methodAuthorization(AdminFnbItemController.class, "updateItem"));
        assertNull(methodAuthorization(AdminFnbComboController.class, "createCombo"));
        assertNull(methodAuthorization(AdminFnbComboController.class, "updateCombo"));
    }

    @Test
    void deletingAndRestoringStayAdminOnly() {
        assertEquals(ADMIN_ONLY, methodAuthorization(AdminFnbItemController.class, "deleteItem").value());
        assertEquals(ADMIN_ONLY, methodAuthorization(AdminFnbItemController.class, "restoreItem").value());
        assertEquals(ADMIN_ONLY, methodAuthorization(AdminFnbComboController.class, "deleteCombo").value());
        assertEquals(ADMIN_ONLY, methodAuthorization(AdminFnbComboController.class, "restoreCombo").value());
    }

    @Test
    void listingDeletedRecordsIsRestrictedToAdmin() {
        String expected = "hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER') and (hasAuthority('ROLE_ADMIN') or !#deleted)";
        assertEquals(expected, methodAuthorization(AdminFnbItemController.class, "searchItems").value());
        assertEquals(expected, methodAuthorization(AdminFnbComboController.class, "searchCombos").value());
    }
}
