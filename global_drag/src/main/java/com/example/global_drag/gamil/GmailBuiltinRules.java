package com.example.global_drag.gamil;

import android.content.Context;
import android.view.View;
import android.view.ViewParent;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class GmailBuiltinRules {
    private static final String PACKAGE_NAME = "com.google.android.gm";
    private static final String TAG = "GmailBuiltinRules";
    private static final String ACT_MAIL = "com.google.android.gm.ui.MailActivityGmail";
    private static final String ACT_CONVERSATION_LIST =
            "com.google.android.gm.ConversationListActivityGmail";
    private static final String ACT_PHOTO = "com.google.android.gm.photo.GmailPhotoViewActivity";
    private static final String ACT_FULL_MESSAGE = "com.google.android.gm.browse.FullMessageActivity";

    private static final String TYPE_IMAGE = "image";
    private static final String TYPE_FILE = "file";
    private static final String TYPE_WEB = "web";
    private static final String FILE_TARGET_TEXT = "text";
    private static final String FILE_DIRS = "cache;files;attachments;download;downloads";
    private static final String FILE_SUFFIX =
            "pdf;doc;docx;xls;xlsx;ppt;pptx;txt;xml;csv;rtf;odt;ods;odp;zip;rar;7z;"
                    + "jpg;jpeg;png;gif;webp;heic;mp3;m4a;wav;ogg;mp4;mkv;mov";
    private static final AtomicInteger sReflectFailureLogs = new AtomicInteger();


    private GmailBuiltinRules() {
    }

    public static boolean isGmailPkg(Context context) {
        return context != null && PACKAGE_NAME.equals(context.getPackageName());
    }

    /**
     * 判断是否是Gmail 附件卡片类名
     *
     * @param view
     * @return
     */
    private static boolean isAttachmentCardClass(View view) {
        String name = view == null ? "" : view.getClass().getSimpleName();
        return "MessageAttachmentTitle".equals(name) || name.endsWith("AttachmentTitle");
    }

    /**
     * 在有限父层级(6层)内确认出点是否位于附件卡片中
     *
     * @param view
     * @return
     */
    private static boolean hasAttachmentCardAncestor(View view) {
        ViewParent parent = view == null ? null : view.getParent();
        int depth = 0;
        while (parent instanceof View && depth++ < 6) {
            if (isAttachmentCardClass((View) parent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * 检查文件是否具有合理文件后缀，降低正文误判概率
     *
     * @param value
     * @return
     */
    private static boolean looksLikeAttachmentFileName(CharSequence value) {
        if (value == null) {
            return false;
        }
        String text = value.toString().trim();
        int dot = text.lastIndexOf(".");
        if (dot <= 0 || dot == text.length() - 1 || text.length() - dot > 12) {
            return false;
        }
        for (int i = dot + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }
}
