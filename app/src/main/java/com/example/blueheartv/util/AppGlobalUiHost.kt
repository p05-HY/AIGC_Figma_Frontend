package com.example.blueheartv.util

import androidx.compose.runtime.Composable

// ============================================================
// Convenience composable that mounts all three global UI hosts.
//
// Usage — place once in your top-level screen Box:
//
//   Box(modifier = Modifier.fillMaxSize()) {
//       // ... your screen content ...
//       AppGlobalUiHost()   // mounts Toast + Dialog + Loading
//   }
//
// Then call from anywhere:
//   ToastUtil.show("保存成功", ToastType.SUCCESS)
//   DialogUtil.showAlert(title = "提示", message = "确认操作？",
//       confirmText = "确定", cancelText = "取消",
//       onConfirm = { /* ... */ })
//   LoadingDialog.show("加载中...")
//   LoadingDialog.dismiss()
// ============================================================

@Composable
fun AppGlobalUiHost() {
    AppToastHost()
    AppDialogHost()
    AppLoadingHost()
}
