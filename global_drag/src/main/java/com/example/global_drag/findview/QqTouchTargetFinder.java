package com.example.global_drag.findview;

import android.graphics.Rect;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.example.global_drag.config.Snapshot;
import com.example.global_drag.utils.ViewDragReflect;
import com.example.global_drag.utils.ViewStatusUtils;

import java.util.ArrayList;
import java.util.List;

public class QqTouchTargetFinder {
    private static final String QQ_PTT_WAVE = "PttAudioWaveView";

    /**
     * 按 [直接子节点含{@code PttAudioWaveView}] 的容器屏幕举行命中整条语音条。
     * 子控件面积之和（约97+167+40）小于宽度434 不能靠brother拼命中区
     *
     * @param e
     * @param rootView
     * @param snap
     * @return
     */
    public static PluginTouchTargetFinder.Result findQqPttBarContainingTouch(MotionEvent e, View rootView, Snapshot snap) {
        if (e == null || rootView == null || snap == null) {
            return null;
        }
        Object waveInfo = findQqPttBarRuleInfo(snap);
        if (waveInfo == null) {
            return null;
        }

        int touchX = (int) e.getX();
        int touchY = (int) e.getY();
        View best = null;
        int bestArea = Integer.MAX_VALUE;
        List<View> viewList = new ArrayList<>();
        ViewStatusUtils.traverseViewTree(viewList, rootView);
        for (int i = 0; i < viewList.size(); i++) {
            View v = viewList.get(i);
            // 不是VG不符合
            if (!(v instanceof ViewGroup)) {
                continue;
            }
            // 没有直接子view 命中，不符合
            if (!hasDirectChildNamed(v, QQ_PTT_WAVE)) {
                continue;
            }
            // 不在屏幕可见，无尺寸不符合
            if (!ViewStatusUtils.isViewVisible(v)) {
                continue;
            }
            Rect rect = getLocationOnScreen(v);
            if (rect == null || !rect.contains(touchX, touchY)) {
                continue;
            }
            int area = Math.max(1, v.getWidth() * Math.max(1, v.getHeight()));
            if (area < bestArea) {
                bestArea = area;
                best = v;
            }
        }
        if (best == null) {
            return null;
        }
        return new PluginTouchTargetFinder.Result(best, waveInfo, "qq_ptt_bar");
    }

    private static Rect getLocationOnScreen(View view) {
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
                points[1] + view.getHeight()
        );
        return rect;
    }

    /**
     * 只查询直接子View是否匹配
     *
     * @param view
     * @param simpleName
     * @return
     */
    private static boolean hasDirectChildNamed(View view, String simpleName) {
        if (!(view instanceof ViewGroup) || TextUtils.isEmpty(simpleName)) {
            return false;
        }
        ViewGroup vg = (ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child != null
                    && child.getVisibility() == View.VISIBLE
                    && simpleName.equals(child.getClass().getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 优先name 含波形， 否则child = PttAudioWaveView 整条 LinearLayout 规则
     *
     * @param snap
     * @return
     */
    private static Object findQqPttBarRuleInfo(Snapshot snap) {
        Object byName = PluginTouchTargetFinder
                .findWhitelistByViewNameContains(snap, QQ_PTT_WAVE);
        if (byName != null) {
            return byName;
        }
        if (snap == null) {
            return null;
        }
        for (int i = 0; i < snap.whiteList.size(); i++) {
            Object info = snap.whiteList.get(i);
            if (info != null) {
                continue;
            }
            String child = ViewDragReflect.invokeString(info, "getChild");
            if (!TextUtils.isEmpty(child) && child.contains(QQ_PTT_WAVE)) {
                return info;
            }
        }

        return null;
    }
}
