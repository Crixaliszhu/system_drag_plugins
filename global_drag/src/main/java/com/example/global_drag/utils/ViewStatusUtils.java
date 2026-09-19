package com.example.global_drag.utils;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.List;

public class ViewStatusUtils {

    /**
     * 不为null，屏幕可见。有尺寸，点击发生在View 或者父组件所在区域，
     *
     * @param view
     * @param touchX
     * @param touchY
     * @return
     */
    public static boolean isValidView(View view, int touchX, int touchY) {
        if (view == null) {
            return false;
        }

        if (!isViewVisible(view)) {
            return false;
        }
        Rect rect = getLocationOnScreen(view);
        if (rect == null || !rect.contains(touchX, touchY)) {
            return false;
        }
        return !isEventOutOfBoundsOfParent(touchX, touchY, view);
    }

    @SuppressLint("CheckResult")
    public static boolean isEventOutOfBoundsOfParent(int x, int y, View view) {
        ViewParent parent = view.getParent();
        Rect availableRect = getLocationOnScreen(view);
        while (parent != null) {
            if (parent instanceof View) {
                Rect parentRect = getLocationOnScreen((View) parent);
                availableRect.intersect(parentRect);
            }
            parent = parent.getParent();
        }
        return !availableRect.contains(x, y);
    }

    public static Rect getLocationOnScreen(View view) {
        if (view == null) {
            return null;
        }
        Rect rect = new Rect();
        int[] points = new int[2];
        view.getLocationInWindow(points);
        rect.set(
                points[0],
                points[1],
                points[0] + view.getWidth(),
                points[1] + view.getHeight());
        return rect;
    }


    public static boolean isViewVisible(View view) {
        return isVisibleToUserCompat(view);
    }

    /**
     * 展示，在屏幕可见，已attached,有尺寸
     *
     * @param view
     * @return
     */
    private static boolean isVisibleToUserCompat(View view) {
        if (view == null) return false;
        if (!view.isAttachedToWindow()) return false;
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        // View只要有一部分在屏幕可见
        return view.getGlobalVisibleRect(new Rect());
    }


    /**
     * 展平子节点后，深度优先记录子节点：根节点在List 前面
     * <p>
     * v1 = VG{
     * v2,
     * v3 = vg{
     * v4,
     * 45 = VG{
     * v7,
     * v8,
     * },
     * v6,
     * }
     * }
     * <p>
     * v1, v2, v3, v4, v5, v6, v7, v8
     *
     * @param out
     * @param view
     */
    public static void traverseViewTree(List<View> out, View view) {
        if (view == null) {
            return;
        }
        out.add(view); // 先加入根节点
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            int n = vg.getChildCount();
            for (int i = 0; i < n; i++) {
                traverseViewTree(out, vg.getChildAt(i));
            }
        }
    }

    /**
     * 同时检查屏幕和窗口相对坐标，兼容弹窗/状态造成的坐标偏移
     *
     * @param v
     * @param root
     * @param x
     * @param y
     * @return
     */
    public static boolean isTouchInEitherCoordinateSpace(View v, View root, int x, int y) {
        Rect r = getLocationOnScreen(v);
        if (r != null && r.contains(x, y)) return true;
        int[] vp = new int[2];
        int[] rp = new int[2];
        v.getLocationInWindow(vp);
        root.getLocationInWindow(rp);
        return new Rect(
                vp[0] - rp[0], vp[1] - rp[1],
                vp[0] - rp[0] + v.getWidth(), vp[1] - rp[1] + v.getHeight()
        ).contains(x, y);
    }
}
