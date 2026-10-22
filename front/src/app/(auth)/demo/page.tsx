"use client";

import { useState } from "react";
import { useAuth } from "../../../contexts/AuthContext";

export default function DemoPage() {
  const {
    authState,
    session,
    userInfo,
    publicResult,
    busy,
    checkPublicEndpoint,
    checkProtectedEndpoint,
    signOut,
  } = useAuth();
  const [message, setMessage] = useState("");
  const authenticated = authState === "authenticated";
  const showSession = session !== null && (authenticated || authState === "unavailable");

  async function handlePublicCheck() {
    setMessage("");
    try {
      await checkPublicEndpoint();
      setMessage("未認証でアクセスできるAPIを確認しました。");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "APIに接続できません。");
    }
  }

  async function handleProtectedCheck() {
    setMessage("");
    try {
      await checkProtectedEndpoint();
      setMessage("Bearer tokenで保護されたAPIへのアクセスに成功しました。");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "認証状態を確認できません。");
    }
  }

  async function handleSignOut(revokeAll: boolean) {
    setMessage("");
    try {
      await signOut(revokeAll);
      setMessage(revokeAll ? "すべてのrefresh tokenを失効しました。" : "ログアウトしました。");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "ログアウト処理に失敗しました。");
    }
  }

  return (
    <main className="workspace">
      <header className="topbar">
        <a className="wordmark" href="#home" aria-label="Refresh Desk home"><span className="wordmark-mark">R</span><span>Refresh Desk</span></a>
        <div className="topbar-meta"><span className="live-dot" /> LDAP AUTH LAB <span className="topbar-divider">/</span> API 01</div>
      </header>

      <section className="intro" id="home">
        <div><p className="eyebrow">IDENTITY / ACCESS CONTROL</p><h1>認証状態を確認する</h1><p className="intro-copy">LDAPサインインと、認証が必要なAPIへのアクセスを確認できます。</p></div>
        <div className={`status-stamp ${authenticated ? "is-on" : ""}`}><span className="status-stamp-label">SESSION</span><strong>{authState === "checking" ? "確認中" : authenticated ? "AUTHENTICATED" : authState === "unavailable" ? "OFFLINE" : "ANONYMOUS"}</strong></div>
      </section>

      <div className="content-grid">
        <section className="auth-panel" aria-labelledby="auth-title">
          <div className="panel-heading"><div><p className="eyebrow">01 / SESSION</p><h2 id="auth-title">セッション</h2></div><span className="panel-index">AUTH</span></div>
          {showSession && (
            <div className="identity-view">
              {authenticated && userInfo && <>
              <div className="identity-avatar">{userInfo.username.slice(0, 1).toUpperCase()}</div>
              <div className="identity-copy"><span>ログイン中のユーザー</span><strong>{userInfo.username}</strong></div>
              <div className="identity-rule" />
              <div className="authority-heading"><span>AUTHORITY</span><span>{userInfo.authorities.length.toString().padStart(2, "0")}</span></div>
              <div className="authority-list">{userInfo.authorities.length ? userInfo.authorities.map((authority) => <span className="authority-tag" key={authority}>{authority}</span>) : <span className="muted">権限情報なし</span>}</div>
              </>}
              <div className="token-note"><span className="token-note-mark">↻</span><span>Access token <strong>{Math.floor((session?.expiresIn ?? 0) / 60)} min</strong><br />期限切れ時はrefresh tokenで更新</span></div>
              <div className="session-actions">
                <button className="button button-primary" onClick={() => void handleProtectedCheck()} disabled={busy}>認証APIを再確認 <span>↗</span></button>
                <button className="button button-quiet" onClick={() => void handleSignOut(false)} disabled={busy}>ログアウト</button>
                <button className="text-button" onClick={() => void handleSignOut(true)} disabled={busy}>全セッションを失効 <span>↗</span></button>
              </div>
            </div>
          )}
          {message && <p className="feedback" role="status">{message}</p>}
          {authState === "unavailable" && <p className="connection-hint">APIに接続できません。APIが起動中か確認してください。</p>}
        </section>

        <section className="checks-panel" aria-labelledby="checks-title">
          <div className="panel-heading"><div><p className="eyebrow">02 / ACCESS CHECK</p><h2 id="checks-title">APIアクセス</h2></div><span className="panel-index">HTTP</span></div>
          <div className="check-row"><div className="check-icon public-icon">○</div><div className="check-detail"><strong>未認証エンドポイント</strong><code>GET /auth/anon</code><span>サインインなしでアクセス可能</span></div><button className="check-action" onClick={() => void handlePublicCheck()} disabled={busy} aria-label="未認証APIを確認">↗</button></div>
          <div className="check-result"><span>RESPONSE</span><code>{publicResult || "未確認"}</code></div>
          <div className="check-row protected-row"><div className="check-icon protected-icon">◆</div><div className="check-detail"><strong>認証必須エンドポイント</strong><code>GET /auth/me</code><span>Bearer tokenが必要</span></div><span className={`access-pill ${authenticated ? "allowed" : "denied"}`}>{authenticated ? "許可" : "未認証"}</span></div>
          <div className="check-result"><span>AUTHORIZATION</span><code>{authenticated ? `Bearer ${session?.token.slice(0, 14)}...` : "Authorization header not set"}</code></div>
          <div className="security-note"><span className="security-note-mark">!</span><p>画面上の表示は確認用です。実際の認可はAPI側のSpring Securityで行われます。</p></div>
        </section>
      </div>
      <footer className="page-footer"><span>DEMO REFRESH TOKEN</span><span>SPRING SECURITY <i /> NEXT.JS</span></footer>
    </main>
  );
}
