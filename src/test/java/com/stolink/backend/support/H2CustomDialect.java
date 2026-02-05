package com.stolink.backend.support;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.dialect.Dialect;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class H2CustomDialect extends H2Dialect {

    public H2CustomDialect() {
        super();
    }

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        try {
            // Approach 1: Try registerColumnType (Backward compatibility)
            Method registerColumnType = null;
            try {
                registerColumnType = getClass().getMethod("registerColumnType", int.class, String.class);
            } catch (NoSuchMethodException e) {
                try {
                    Method m = H2Dialect.class.getDeclaredMethod("registerColumnType", int.class, String.class);
                    m.setAccessible(true);
                    registerColumnType = m;
                } catch (Exception ex) {
                    // Method not found, proceed to next approach
                }
            }

            if (registerColumnType != null) {
                registerColumnType.invoke(this, SqlTypes.VECTOR, "varchar");
                return;
            }

            // Approach 2: DdlTypeRegistry (Modern API)
            Object registry = typeContributions.getTypeConfiguration().getDdlTypeRegistry();
            Method addMethod = null;

            for (Method m : registry.getClass().getMethods()) {
                if (m.getName().equals("addDescriptor") && m.getParameterCount() == 1) {
                    addMethod = m;
                }
            }

            if (addMethod != null) {
                Class<?> ddlTypeClass = Class.forName("org.hibernate.type.descriptor.sql.internal.DdlTypeImpl");
                Constructor<?> constructor = ddlTypeClass.getConstructor(int.class, String.class, Dialect.class);
                Object ddlTypeInstance = constructor.newInstance(SqlTypes.VECTOR, "varchar", this);
                addMethod.invoke(registry, ddlTypeInstance);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
