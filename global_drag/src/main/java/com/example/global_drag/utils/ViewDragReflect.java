package com.example.global_drag.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ViewDragReflect {

    public static String invokeString(Object obj, String name) {
        return null;
    }

    private static void getClassObject(Object obj) {
        Class<?> clazz = obj.getClass();

        try {
            Class<?> clazz2 = Class.forName("com.example.User");
            Object o = clazz2.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            //
        }
    }

    /**
     * 1. 访问私有成员需要 setAccessible(true)
     * 2. 参数类型要精确匹配 比如int.class 和 Integer.class是不同的；
     * 3. 常见异常：ClassNotFoundException, NoSuchFieldException, IllegalAccessException,InvocationTargetException
     * 4. 反射性能差，平凡调用可缓存Field / Method对象；
     * 5. Android 混淆会导致反射失败；
     * 6. Java 9+ 模块化，如果一个类在另一个模块，setAccessible 可能被禁止，需要 --add-opens
     */
    private static void reflectDemo() {
        try {
            // 获取class
            Class<?> clazz = Class.forName("com.example.global_drag.utils.ViewDragReflect.User");
            // 创建示例
            Object user = clazz.getDeclaredConstructor().newInstance();
            // 获取属性
            Field namField = clazz.getDeclaredField("name");
            // 允许访问
            namField.setAccessible(true);
            // 设置值
            namField.set(user, "王五");
            // 获取属性值
            System.out.println("name = " + namField.get(user));

            // 调用public函数
            Method setName = clazz.getMethod("setName", String.class);
            setName.invoke(user, "赵六");

            Method getName = clazz.getMethod("getName");
            Object name = getName.invoke(user);
            System.out.println("getName = " + name);

            Method secret = clazz.getDeclaredMethod("secret", String.class);
            secret.setAccessible(true);
            Object sr = secret.invoke(user,"Hello");
            System.out.println("secret = " + sr);

            Field countField = clazz.getDeclaredField("COUNT");
            countField.setAccessible(true);
            // 静态字段设置，不需要对象实例；
            countField.set(null, 10);

            // 静态方法调用 不需要实例对象
            Method printCount = clazz.getMethod("printCount");
            printCount.invoke(null);

        } catch (Throwable t) {
            //
        }
    }

    private static final class User {
        public String name;
        private int age;
        public static int COUNT = 0;

        public User() {
        }

        public void setAge(int age) {
            this.age = age;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public String getName() {
            return name;
        }

        public static void printCount() {
            //
        }

        private String secret(String input) {
            return "secret: " + input;
        }
    }
}
