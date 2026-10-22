"use client";

import { type FormEvent, useState } from "react";
import { useAuth } from "../../contexts/AuthContext";

export default function SignInPage() {
  const { signIn, busy, authState } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage("");
    try {
      await signIn(username, password);
      setPassword("");
    } catch {
      setMessage("サインインできませんでした。アカウント情報とAPIの接続状態を確認してください。");
    }
  }

  return (
    <main className="workspace">
      <header className="topbar"><span className="wordmark">Refresh Desk</span></header>
      <section className="intro"><h1>サインイン</h1></section>
      <form className="sign-in-form" onSubmit={handleSubmit}>
        <label htmlFor="username">ユーザー名</label>
        <input id="username" name="username" autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} required disabled={busy} />
        <label htmlFor="password">パスワード</label>
        <input id="password" name="password" type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required disabled={busy} />
        <button className="button button-primary" type="submit" disabled={busy}>{busy ? "接続中..." : "サインイン"}</button>
      </form>
      {message && <p role="alert" className="feedback">{message}</p>}
      {authState === "unavailable" && <p className="connection-hint">APIに接続できません。APIが起動中か確認してください。</p>}
    </main>
  );
}
