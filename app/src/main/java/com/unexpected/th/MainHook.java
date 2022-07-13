package com.unexpected.th;

import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.WindowManager;

import com.unexpected.th.XC_MethodAfterReplacement;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if(lpparam.packageName.equals("org.telegram.messenger") || lpparam.packageName.equals("org.telegram.plus")) {
            XposedHelpers.findAndHookMethod("android.view.Window", lpparam.classLoader, "setFlags", int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        Integer flags = (Integer) param.args[0];
                        flags &= ~ WindowManager.LayoutParams.FLAG_SECURE;
                        param.args[0] = flags;
                    }
                });

            XposedHelpers.findAndHookMethod("android.view.SurfaceView", lpparam.classLoader, "setSecure", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        param.args[0] = false;
                    }
                });

            XposedHelpers.findAndHookMethod("org.telegram.ui.ChatActivity", lpparam.classLoader, "hasSelectedNoforwardsMessage", XC_MethodAfterReplacement.returnConstant(false));
            XposedHelpers.findAndHookMethod("org.telegram.ui.Components.SharedMediaLayout", lpparam.classLoader, "hasNoforwardsMessage", XC_MethodAfterReplacement.returnConstant(false));
            XposedBridge.hookAllMethods(XposedHelpers.findClass("org.telegram.messenger.MessagesController", lpparam.classLoader), "isChatNoForwards", XC_MethodAfterReplacement.returnConstant(false));
            XposedHelpers.findAndHookMethod("org.telegram.messenger.UserConfig", lpparam.classLoader, "isPremium", XC_MethodAfterReplacement.returnConstant(true));
            
            // Disable Restriction
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                    long tempId;
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        final Context context = (Context) param.thisObject;
                        final Class<?> messagesController = XposedHelpers.findClass("org.telegram.messenger.MessagesController", lpparam.classLoader);
                        final Class<?> baseFragment = XposedHelpers.findClass("org.telegram.ui.ActionBar.BaseFragment", lpparam.classLoader);
                        XposedHelpers.findAndHookMethod(messagesController, "checkCanOpenChat", Bundle.class, baseFragment, XposedHelpers.findClass("org.telegram.messenger.MessageObject", lpparam.classLoader), new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                    if(!(param.args[0] == null || param.args[1] == null)) {
                                        long userId = ((Bundle) param.args[0]).getLong("user_id", 0);
                                        long chatId = ((Bundle) param.args[0]).getLong("chat_id", 0);
                                        Object user = null;
                                        Object chat;
                                        Object str;
                                        if(userId != 0) {
                                            chat = null;
                                            user = XposedHelpers.callMethod(param.thisObject, "getUser", Long.valueOf(userId));
                                        } else {
                                            chat = chatId != 0 ? XposedHelpers.callMethod(param.thisObject, "getChat", Long.valueOf(chatId)) : null;
                                        }
                                        if(user != null || chat != null) {
                                            if(chat != null) {
                                                str = XposedHelpers.callStaticMethod(messagesController, "getRestrictionReason", XposedHelpers.getObjectField(chat, "restriction_reason"));
                                            } else {
                                                str = XposedHelpers.callStaticMethod(messagesController, "getRestrictionReason", XposedHelpers.getObjectField(user, "restriction_reason"));
                                            }
                                            if(str != null) {
                                                if(chat != null) {
                                                    userId = chatId;
                                                }
                                                tempId = userId;
                                            }
                                        }
                                    }
                                }
                            });
                        XposedHelpers.findAndHookMethod(messagesController, "showCantOpenAlert", baseFragment, String.class, new XC_MethodHook() {
                                Object user;
                                Object chat;
                                @Override
                                protected void afterHookedMethod(final XC_MethodHook.MethodHookParam param) throws Throwable {
                                    if(param.args[0] != null && XposedHelpers.callMethod(param.args[0], "getParentActivity") != null) {
                                        Object builder = XposedHelpers.newInstance(XposedHelpers.findClass("org.telegram.ui.ActionBar.AlertDialog.Builder", lpparam.classLoader),
                                            XposedHelpers.callMethod(param.args[0], "getParentActivity"));
                                        XposedHelpers.callMethod(builder, "setTitle", XposedHelpers.callMethod(XposedHelpers.findClass("org.telegram.messenger.LocaleController", lpparam.classLoader).newInstance(), "getString", "AppName", context.getResources().getIdentifier("AppName", "string", lpparam.packageName)));
                                        XposedHelpers.callMethod(builder, "setPositiveButton", XposedHelpers.callMethod(XposedHelpers.findClass("org.telegram.messenger.LocaleController", lpparam.classLoader).newInstance(), "getString", "OK", context.getResources().getIdentifier("OK", "string", lpparam.packageName)), null);
                                        XposedHelpers.callMethod(builder, "setMessage", param.args[1]);
                                        Object selectedAccount = XposedHelpers.callStaticMethod(messagesController, "getInstance", XposedHelpers.getStaticIntField(XposedHelpers.findClass("org.telegram.messenger.UserConfig", lpparam.classLoader), "selectedAccount"));
                                        user = XposedHelpers.callMethod(selectedAccount, "getUser", Long.valueOf(tempId));
                                        chat = XposedHelpers.callMethod(selectedAccount, "getChat", Long.valueOf(tempId));
                                        String str = null;
                                        if(user != null) {
                                            if(XposedHelpers.getBooleanField(user, "min")) {
                                                user = null;
                                            } else {
                                                str = (String) XposedHelpers.callStaticMethod(messagesController, "getRestrictionReason", XposedHelpers.getObjectField(user, "restriction_reason"));
                                            }
                                        } else if(chat != null) {
                                            if(XposedHelpers.getBooleanField(chat, "min")) {
                                                chat = null;
                                            } else {
                                                str = (String) XposedHelpers.callStaticMethod(messagesController, "getRestrictionReason", XposedHelpers.getObjectField(chat, "restriction_reason"));
                                            }
                                        }
                                        if(str != null) {
                                            XposedHelpers.callMethod(builder, "setMessage", str);
                                        }
                                        XposedHelpers.callMethod(builder, "setNegativeButton", XposedHelpers.callMethod(XposedHelpers.findClass("org.telegram.messenger.LocaleController", lpparam.classLoader).newInstance(), "getString", "Open", context.getResources().getIdentifier("Open", "string", lpparam.packageName)), new DialogInterface.OnClickListener(){
                                                @Override
                                                public void onClick(DialogInterface p1, int p2) {
                                                    final Bundle b = new Bundle(1);
                                                    if(chat != null) {
                                                        b.putLong("chat_id", XposedHelpers.getLongField(chat, "id"));
                                                    } else {
                                                        b.putLong("user_id", XposedHelpers.getLongField(user, "id"));
                                                    }
                                                    XposedHelpers.callMethod(param.args[0], "presentFragment", XposedHelpers.newInstance(XposedHelpers.findClass("org.telegram.ui.ChatActivity", lpparam.classLoader), b), false);
                                                }
                                            });
                                        XposedHelpers.callMethod(builder, "setNeutralButton", XposedHelpers.callMethod(XposedHelpers.findClass("org.telegram.messenger.LocaleController", lpparam.classLoader).newInstance(), "getString", "OpenProfile", context.getResources().getIdentifier("OpenProfile", "string", lpparam.packageName)), new DialogInterface.OnClickListener(){
                                                @Override
                                                public void onClick(DialogInterface p1, int p2) {
                                                    final Bundle b = new Bundle(1);
                                                    if(chat != null) {
                                                        b.putLong("chat_id", XposedHelpers.getLongField(chat, "id"));
                                                    } else {
                                                        b.putLong("user_id", XposedHelpers.getLongField(user, "id"));
                                                    }
                                                    XposedHelpers.callMethod(param.args[0], "presentFragment", XposedHelpers.newInstance(XposedHelpers.findClass("org.telegram.ui.ProfileActivity", lpparam.classLoader), b));
                                                }
                                            });
                                        XposedHelpers.callMethod(param.args[0], "showDialog", XposedHelpers.callMethod(builder, "create"));
                                    }
                                }
                            });
                    }
                });
        }
    }
}
