"use client";

import { type ReactNode, useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "./AuthContext";

export function AuthGate({ children }: { children: ReactNode }) {
  const { authState, session, busy, checkProtectedEndpoint, signOut } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [message, setMessage] = useState("");
  const isSignInPage = pathname === "/signin";

  useEffect(() => {
    if (authState === "authenticated" && isSignInPage) {
      router.replace("/demo");
    } else if (!isSignInPage && (authState === "anonymous" || (authState === "unavailable" && !session))) {
      router.replace("/signin");
    }
  }, [authState, isSignInPage, router, session]);

  async function recover(logout: boolean) {
    setMessage("");
    try {
      if (logout) await signOut(false);
      else await checkProtectedEndpoint();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "APIに接続できません。");
    }
  }

  if (authState === "checking") {
    return <main className="workspace"><p role="status">認証状態を確認中...</p></main>;
  }

  if (authState === "unavailable" && session) {
    return (
      <main className="workspace">
        <section className="intro"><h1>接続を確認できません</h1></section>
        <p className="connection-hint">APIに接続できません。APIが起動中か確認してください。</p>
        <div className="session-actions">
          <button className="button button-primary" onClick={() => void recover(false)} disabled={busy}>認証APIを再確認</button>
          <button className="button button-quiet" onClick={() => void recover(true)} disabled={busy}>ログアウト</button>
        </div>
        {message && <p role="status" className="feedback">{message}</p>}
      </main>
    );
  }

  if ((authState === "authenticated" && !isSignInPage) || (isSignInPage && authState !== "authenticated")) {
    return children;
  }

  return <main className="workspace"><p role="status">移動中...</p></main>;
}
