package com.example.global_drag.findview;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.example.global_drag.DragManager;
import com.example.global_drag.OplusDragPrepareExtras;
import com.example.global_drag.config.Snapshot;
import com.example.global_drag.config.WhiteBlackViewListLoader;
import com.example.global_drag.utils.ViewDragReflect;
import com.example.global_drag.utils.ViewStatusUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 长按事件触发时，touch目标View查找器：全局拖拽启动第一步查找目标View
 */
public class PluginTouchTargetFinder {

    /**
     * 目标View信息实体类
     * {@link #find} 输出： 选中的target 以及其匹配到的{@code ViewDragInfo}
     */
    public static final class Result {
        /**
         * 目标view
         */
        public final View target;
        /**
         * 目标view的信息
         */
        public final Object matchInfo;
        /**
         * 目标view的路径
         */
        public final String path;

        Result(View target, Object matchInfo, String path) {
            this.matchInfo = matchInfo;
            this.target = target;
            this.path = path;
        }
    }

    /**
     * 目标view匹配
     *
     * @param extras
     * @return 为null则未找到，本次拖拽事件无效
     */
    public static Result find(Bundle extras) {
        return find(extras, null);
    }

    /**
     * 带失败原因的采集查找入口，返回{@code null}时，{@code failureReasonOut}中
     * 追加一条本次 miss的结构化原因标签（多次失败原因以{@code ';'}拼接）
     *
     * @param extras           查找参数
     * @param failureReasonOut 若为null，标识调用方不关心失败原因
     * @return
     */
    public static Result find(Bundle extras, StringBuilder failureReasonOut) {
        if (extras == null) {
            appendReason(failureReasonOut, "request_null");
            return null;
        }
        // todo DragManager
        View rootView = DragManager.rootViewOf();
        // todo OplusDragPrepareExtras
        MotionEvent e = OplusDragPrepareExtras.getMotionEvent(extras);
        if (rootView == null || e == null) {
            appendReason(failureReasonOut, "request_incomplete:rootView = " +
                    (rootView != null)
                    + ", motionEvent = " +
                    (e != null));
            return null;
        }
        // todo Snapshot, WhiteBlackViewListLoader：目标View不在白名单配置
        Snapshot snap = WhiteBlackViewListLoader.load(extras, rootView);
        if (snap == null) {
            appendReason(failureReasonOut, "snapshot _null");
            return null;
        }
        try {
            Result result = doFind(e, rootView, snap);
            if (result == null) {
                appendReason(failureReasonOut, "no_match:touchXY=(" +
                        (int) e.getX() + "," +
                        (int) e.getY() + ")");
            }
            return result;
        } catch (Throwable t) {
            appendReason(failureReasonOut, "finder_throw" + t.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 目标view查找逻辑
     *
     * @param e
     * @param rootView
     * @param snap
     * @return
     */
    private static Result doFind(MotionEvent e, View rootView, Snapshot snap) {
        boolean fileFirst = snap.activitySupportFileDrag;
        if (fileFirst) {
            Result afterBfs = findWithBfs(e, rootView, snap);
            if (afterBfs != null && isTouchView(afterBfs.matchInfo)) {
                Result afterDispatch = findWithEventDispatch(e, snap);

            }
            return afterBfs;
        }
    }

    private static boolean isTouchView(Object info) {
        if (info == null) {
            return false;
        }
        Object v = ViewDragReflect.invokeString(info, "isTouchView");
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        String s = ViewDragReflect.invokeString(info, "getTouchView");
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    /**
     * @param e
     * @param rootView
     * @param snap
     * @return
     */
    private static Result findWithBfs(MotionEvent e, View rootView, Snapshot snap) {
        // QQ语音：整条 LinearLayout≈434x128,子视图拼不满 (97+167+40); 按条容器矩形优先命中
        Result pttBar = QqTouchTargetFinder.findQqPttBarContainingTouch(e, rootView, snap);
        if (pttBar != null) {
            // 命中QQ语音
            return pttBar;
        }
        List<View> viewList = new ArrayList<>();
        ViewStatusUtils.traverseViewTree(viewList, rootView);
        View target = null;
        Object targetInfo = null;
        for (int i = viewList.size() - 1; i >= 0; i--) {
            View v = viewList.get(i);
            if (!ViewStatusUtils.isValidView(v, (int) e.getX(), (int) e.getY())) {
                continue;
            }
            if (isInvalidImageView(v)) {
                continue;
            }
            Object info = getValidViewInfo(v, snap);
            if (info == null) {
                continue;
            }
            target = promoteQqPttBarTarget(v, info);
            targetInfo = info;
            break;
        }

        if (target != null && !isFileLikeType(targetInfo)) {
            target == applyCoverDrop(e, viewList, target, targetInfo);
        }
        if (target == null) {
            Result outlookWeb = ConversationTargetFinder.findOutlookConversationWebView(e, rootView, snap);
            if (outlookWeb != null) {
                return outlookWeb;
            }
            Result gmailWeb = ConversationTargetFinder.findGmailConversationWebView(e, rootView, snap);
            if (gmailWeb != null) {
                return gmailWeb;
            }
            return null;
        }

        return new Result(target, targetInfo, "bfs");
    }


    /**
     * 从白名单中查找View
     *
     * @param snap
     * @param namePart
     * @return
     */
    public static Object findWhitelistByViewNameContains(Snapshot snap, String namePart) {
        if (snap == null || TextUtils.isEmpty(namePart)) {
            return null;
        }

        for (int i = 0; i < snap.whiteList.size(); i++) {
            Object info = snap.whiteList.get(i);
            if (info == null) {
                continue;
            }
            String vn = ViewDragReflect.invokeString(info, "getViewName");
            if (!TextUtils.isEmpty(vn) && vn.contains(namePart)) {
                return info;
            }
        }
        return null;
    }

    /**
     * 最佳reason到缓冲区，多条原因以 {@code ';'} 分割; {@code sb == null}时忽略
     */
    private static void appendReason(StringBuilder sb, String reason) {
        if (sb == null || reason == null) {
            return;
        }
        if (sb.length() != 0) {
            sb.append(";");
        }
        sb.append(reason);
    }
}
