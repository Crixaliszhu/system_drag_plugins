package com.example.global_drag.findview;

import android.view.MotionEvent;
import android.view.View;

import com.example.global_drag.config.Snapshot;
import com.example.global_drag.utils.ViewDragReflect;
import com.example.global_drag.utils.ViewStatusUtils;

import java.util.ArrayList;
import java.util.List;

public class ConversationTargetFinder {
    /** Outlook 邮件头和正文都位于 conversation_webview；WebView 内部 DOM 不会出现在 Android 子树中。 */
    /**
     * Outlook 专用入口：仅在目标包中执行 Conversation WebView 兜底。
     */
    public static PluginTouchTargetFinder.Result findOutlookConversationWebView(MotionEvent e, View rootView, Snapshot snap) {
        if (e == null || rootView == null || snap == null
                || !"com.microsoft.office.outlook".equals(rootView.getContext().getPackageName())) {
            return null;
        }
        return findConversationWebView(e, rootView, snap, "Outlook");
    }

    /** Gmail 正文、链接和内联图片同样位于唯一 ConversationWebView 的 DOM 中。 */
    /**
     * Gmail 专用入口：正文、链接和内联图片统一映射到 Web 类型规则。
     */
    public static PluginTouchTargetFinder.Result findGmailConversationWebView(MotionEvent e, View rootView, Snapshot snap) {
        if (e == null || rootView == null || snap == null
                || !"com.google.android.gm".equals(rootView.getContext().getPackageName())) {
            return null;
        }
        return findConversationWebView(e, rootView, snap, "Gmail");
    }

    /**
     * @param e
     * @param rootView
     * @param snap
     * @param appName
     * @return
     */
    private static PluginTouchTargetFinder.Result findConversationWebView(
            MotionEvent e, View rootView, Snapshot snap, String appName
    ) {
        int x = (int) e.getX();
        int y = (int) e.getY();
        List<View> views = new ArrayList<>();
        ViewStatusUtils.traverseViewTree(views, rootView);
        List<View> touchWebViews = new ArrayList<>();
        for (int i = views.size() - 1; i >= 0; i--) {
            View v = views.get(i);
            String simple = v.getClass().getSimpleName();
            if (!("WebView".equals(simple) || v.getClass().getName().endsWith("WebView"))) {
                continue;
            }
            String entry = "";
            try {
                if (v.getId() != View.NO_ID && v.getId() != 0) {
                    // 资源id名： @+id/btn_name: btn_name, getResourceName() 则得到完整资源名："com.xx.app:id/btn_name"
                    entry = v.getResources().getResourceEntryName(v.getId());
                }
            } catch (Throwable r) {
                //
            }
            if (!ViewStatusUtils.isTouchInEitherCoordinateSpace(v, rootView, x, y)) {
                continue;
            }
            // 使用资源id名匹配
            if ("conversation_webview".equals(entry) || "webview".equals(entry)) {
                Object info = findWebRuleInfo(snap, true);
                if (info != null) {
                    return new PluginTouchTargetFinder.Result(v, info, appName.toLowerCase() + "_web_fallback");
                }
            }
            touchWebViews.add(v);
        }
        if (touchWebViews.size() == 1) {
            Object info = findWebRuleInfo(snap, false);
            if (info != null) {
                View v = touchWebViews.get(0);
                return new PluginTouchTargetFinder.Result(v, info, appName.toLowerCase() + "_web_fallback_unique");
            }
        }
        return null;
    }

    /**
     * 查找Web规则； 严格要求 conversation_webview / webView ID
     *
     * @param snap
     * @param requireKnownId
     * @return
     */
    private static Object findWebRuleInfo(Snapshot snap, boolean requireKnownId) {
        for (Object info : snap.whiteList) {
            String type = ViewDragReflect.invokeString(info, "getType");
            String id = ViewDragReflect.invokeString(info, "getId");
            if ("web".equals(type)
                    && (!requireKnownId || "conversation_webview".equals(id) || "webView".equals(id))) {
                return info;
            }
        }
        return null;
    }
}
