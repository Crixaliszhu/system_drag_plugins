package com.example.global_drag.gamil;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewParent;

import com.example.global_drag.config.PluginDragFileInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class GmailPathResolver {
    private static final String GMAIL_PKG = "com.google.android.gm";
    // 是AOSP-Android 开源项目邮件应用UnifiedEmail中的数据模型类，用于描述右键附件的各种信息；public class Attachment implements Parcelable
    // 位于UnifiedEmail 项目的providers 报下，这个包里的类通常都对应这数据库中的一张表，负责封装数据并与ContentProvider交互；
    private static final String ATTACHMENT_CLASS = "com.android.mail.attachment.Attachment";
    private static final int MAX_PARENT_DEPTH = 10;
    private static final int MAX_OBJECT_DEPTH = 6;
    private static final int MAX_OBJECTS = 240;
    private static final int MAX_REFLECT_FAILURE_LOGS = 12;
    private static final AtomicInteger sReflectFailureLogs = new AtomicInteger();

    private GmailPathResolver() {
    }

    static boolean isGmailPkg(Context context) {
        return context != null && GMAIL_PKG.equals(context.getPackageName());
    }


    /**
     * Gmail 附件解析主入口：从触摸点View，父节点，tag/listener 和 Attachment 对象图中查找content Uri
     * 或 本地路径；未命中时在按照附件名查询，MediaStore 和文件目录
     *
     * @return
     */
    static String tryResolve(
            Context context, View view, PluginDragFileInfo info, String nameHilt
    ) {
        if (!isGmailPkg(context) || view == null || info == null) {
            return null;
        }

        Queue<Node> queue = new ArrayDeque<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        sReflectFailureLogs.set(0);
        View current = view;
        for (int depth = 0; current != null && depth < MAX_PARENT_DEPTH; depth++) {
            offer(queue, current, 0, "view", seen);
            offer(queue, current.getTag(), 0, "tag", seen);
            Object listenerInfo = tryGetListenerInfoFromView(current);
            offer(queue, findClickListenerInstance(listenerInfo), 0, "clickListener", seen);
            offer(queue, findLongClickLisenerInstance(listenerInfo), 0, "longClickListener", seen);
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        int visited = 0;
        while (!queue.isEmpty() && visited++ < MAX_OBJECTS) {
            Node node = queue.poll();
            String direct = resolveDirect(node.value);
            if (!TextUtils.isEmpty(direct)) {
                return direct;
            }
        }
    }

    private static void collectFields(Node node, Queue<Node> queue, Set<Object> seen) {
        Class<?> type = node.value.getClass();
        for (int classDepth = 0;
             type != null && type != Object.class && classDepth < 6;
             classDepth++, type = type.getSuperclass()
        ) {
            Field[] fields;
            try {
                fields = type.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }
            for (Field field : fields) {
                try {
                    //是否是 基本数据类型，静态字段
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                        continue;
                    }
                    if (!isInterestingField(node.value, field)) {
                        continue;
                    }
                    field.setAccessible(true);
                    offer(queue, field.get(node.value), node.depth + 1, field.getName(), seen);
                } catch (Throwable t) {
                    //
                }
            }
        }
    }

    /**
     * 根据类名，包路径和Attachment 后缀识别Gmail 附件模型对象
     *
     * @param value
     * @return
     */
    private static boolean isAttachmentObject(Object value) {
        if (value == null) {
            return false;
        }
        String className = value.getClass().getName();
        String lower = className.toLowerCase();
        return ATTACHMENT_CLASS.equals(className)
                || lower.contains(".attachment")
                || lower.contains("attachment")
                || lower.contains("attachmentmode");
    }

    /**
     * 仅保留URI, 路径，文件，下载和附件相关字段，控制反射搜索范围
     *
     * @param owner
     * @param field
     * @return
     */
    private static boolean isInterestingField(Object owner, Field field) {
        String ownerName = owner.getClass().getName();
        String fieldName = field.getName().toLowerCase();
        String typeName = field.getType().getName();
        // field是否可以安全复制个Uri, field是Uri的子类，子接口实现类，或者同一类型；
        // 注意：基本类型和包装类型不通用
        if (Uri.class.isAssignableFrom(field.getType()) ||
                File.class.isAssignableFrom(field.getType()) ||
                ATTACHMENT_CLASS.equals(typeName)) {
            return true;
        }
        if (fieldName.contains("attachment") || fieldName.contains("uri")
                || fieldName.contains("file") || fieldName.equals("this$0")
                || fieldName.startsWith("val$")) {
            return true;
        }
        // Gmail 业务类经过R8 混淆（如xim/mwb）,字段名通常只有一个字符。
        return owner instanceof View
                || ownerName.startsWith("com.android.mail.")
                || ownerName.length() <= 5;
    }

    /**
     * 将uri File或路径字符串转换成可继续使用的附件地址
     *
     * @param value
     * @return
     */
    private static String resolveDirect(Object value) {
        if (value instanceof Uri) {
            Uri uri = (Uri) value;
            if (Uri.EMPTY.equals(uri)) {
                return null;
            }
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                return uri.toString();
            }

            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return existFile(uri.getPath());
            }
        } else if (value instanceof File) {
            return existFile(((File) value).getAbsolutePath());
        } else if (value instanceof CharSequence) {
            String text = value.toString().trim();
            if (text.startsWith("content://")) {
                return text;
            }
            if (text.startsWith("file://")) {
                return existFile(Uri.parse(text).getPath());
            }
            if (text.startsWith("/")) {
                return existFile(text);
            }
        }
        return null;
    }

    /**
     * 验证路径存在且是普通文件，避免返回目录或失效路径
     *
     * @param path
     * @return
     */
    private static String existFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        return file.isFile() && file.length() > 0 ? file.getAbsolutePath() : null;
    }

    /**
     * 判断对象是否为无需继续反射展开的叶子值
     *
     * @param value
     * @return
     */
    private static boolean isLeaf(Object value) {
        return value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Character
                || value instanceof Enum<?> || value instanceof Class<?>;
    }

    /**
     * 将未访问，未超深度且非空的对象加入BFS队列
     *
     * @param queue
     * @param value
     * @param depth
     * @param edgeName
     * @param seen
     */
    private static void offer(
            Queue<Node> queue, Object value, int depth, String edgeName, Set<Object> seen
    ) {
        if (value != null && seen.add(value)) {
            // 默认插入尾部（BlockingQueue, ArrayQueue,LinkedList...）, PriorityQueue插入后要按优先级排序
            queue.offer(new Node(value, depth, edgeName));
        }
    }

    private static final class Node {
        final Object value;
        final int depth;
        final String edgeName;

        Node(Object value, int depth, String edgeName) {
            this.value = value;
            this.depth = depth;
            this.edgeName = edgeName;
        }
    }
}
