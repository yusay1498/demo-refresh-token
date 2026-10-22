import { StrictMode } from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Home from "../app/page";
import { AuthProvider, type AuthSession, useAuth } from "./AuthContext";

const sessionKey = "demo-refresh-token.auth";
const savedSession: AuthSession = {
  token: "old-access-token",
  refreshToken: "old-refresh-token",
  username: "demo-user",
  authorities: ["ROLE_USER"],
  tokenType: "Bearer",
  expiresIn: 900,
};
const refreshedSession = { ...savedSession, token: "new-access-token", refreshToken: "new-refresh-token" };
const userInfo = { username: savedSession.username, authorities: savedSession.authorities };
const fetchMock = vi.fn<typeof fetch>();

function Probe() {
  const auth = useAuth();
  return <>
    <output data-testid="state">{auth.authState}</output>
    <output data-testid="session">{auth.session?.token ?? "none"}</output>
    <output data-testid="public">{auth.publicResult}</output>
    <button onClick={() => void auth.checkPublicEndpoint()}>Public check</button>
    <button onClick={() => void auth.signIn("demo-user", "test-password")}>Sign in</button>
  </>;
}

function renderAuth(children = <Home />) {
  return render(<StrictMode><AuthProvider>{children}</AuthProvider></StrictMode>);
}

beforeEach(() => {
  window.sessionStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("session recovery", () => {
  it.each(["offline", "503"])("retains session on %s and offers retry without userInfo", async failure => {
    window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
    if (failure === "offline") fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    else fetchMock.mockResolvedValueOnce(new Response(null, { status: 503 }));
    renderAuth();
    await screen.findByText("OFFLINE");
    expect(screen.queryByLabelText("パスワード")).toBeNull();
    expect(screen.getByRole("button", { name: "ログアウト" })).toBeTruthy();
    expect(screen.getByText("APIに接続できません。APIが起動中か確認してください。")).toBeTruthy();
    expect(JSON.parse(window.sessionStorage.getItem(sessionKey)!)).toEqual(savedSession);

    fetchMock.mockResolvedValueOnce(Response.json(userInfo));
    fireEvent.click(screen.getByRole("button", { name: /認証APIを再確認/ }));
    await screen.findByText("AUTHENTICATED");
    expect(screen.getByText("demo-user")).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("allows logout without restored userInfo", async () => {
    window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    renderAuth();
    await screen.findByText("OFFLINE");
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    fireEvent.click(screen.getByRole("button", { name: "ログアウト" }));
    await screen.findByText("ANONYMOUS");
    expect(window.sessionStorage.getItem(sessionKey)).toBeNull();
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/\/auth\/signOut$/);
    expect(JSON.parse(fetchMock.mock.calls[1][1]!.body as string)).toEqual({ refreshToken: savedSession.refreshToken });
  });

  it.each(["offline", "503"])("keeps controls after a protected check fails with %s", async failure => {
    window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
    fetchMock.mockResolvedValueOnce(Response.json(userInfo));
    renderAuth();
    await screen.findByText("AUTHENTICATED");
    if (failure === "offline") fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    else fetchMock.mockResolvedValueOnce(new Response(null, { status: 503 }));
    fireEvent.click(screen.getByRole("button", { name: /認証APIを再確認/ }));
    await screen.findByText("OFFLINE");
    expect(screen.getByRole("button", { name: /認証APIを再確認/ })).toBeTruthy();
    expect(screen.getByRole("button", { name: "ログアウト" })).toBeTruthy();
    expect(JSON.parse(window.sessionStorage.getItem(sessionKey)!)).toEqual(savedSession);
  });

  it.each(["invalid JSON", "null", '{"token":123}', JSON.stringify({ ...savedSession, refreshToken: "" })])("discards malformed saved data: %s", async saved => {
    window.sessionStorage.setItem(sessionKey, saved);
    renderAuth(<Probe />);
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("anonymous"));
    expect(window.sessionStorage.getItem(sessionKey)).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("discards a session when refresh is rejected with 401", async () => {
    window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    renderAuth(<Probe />);
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("anonymous"));
    expect(window.sessionStorage.getItem(sessionKey)).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

describe("public response ordering", () => {
  it.each(["signIn", "restore"])("does not overwrite completed %s with a delayed public response", async scenario => {
    let resolvePublic!: (response: Response) => void;
    let resolveRestore!: (response: Response) => void;
    if (scenario === "restore") {
      window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
      fetchMock.mockImplementationOnce(() => new Promise(resolve => { resolveRestore = resolve; }));
    } else {
      fetchMock.mockResolvedValueOnce(new Response("anonymous"));
    }
    renderAuth(<Probe />);
    if (scenario === "signIn") {
      await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("anonymous"));
    }
    fetchMock.mockImplementationOnce(() => new Promise(resolve => { resolvePublic = resolve; }));
    fireEvent.click(screen.getByRole("button", { name: "Public check" }));
    if (scenario === "restore") {
      await act(async () => resolveRestore(Response.json(userInfo)));
    } else {
      fetchMock.mockResolvedValueOnce(Response.json(savedSession));
      fetchMock.mockResolvedValueOnce(Response.json(userInfo));
      fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    }
    await waitFor(() => expect(screen.getByTestId("state").textContent).toBe("authenticated"));
    await act(async () => resolvePublic(new Response("delayed public result")));
    await waitFor(() => expect(screen.getByTestId("public").textContent).toBe("delayed public result"));
    expect(screen.getByTestId("state").textContent).toBe("authenticated");
    expect(screen.getByTestId("session").textContent).toBe(savedSession.token);
  });
});

describe("signOutAll retry", () => {
  async function restore() {
    window.sessionStorage.setItem(sessionKey, JSON.stringify(savedSession));
    fetchMock.mockResolvedValueOnce(Response.json(userInfo));
    renderAuth();
    await screen.findByText("AUTHENTICATED");
  }

  it("saves rotated tokens before retrying once with the new access token", async () => {
    await restore();
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    fetchMock.mockResolvedValueOnce(Response.json(refreshedSession));
    fetchMock.mockImplementationOnce(async (_input, init) => {
      expect(JSON.parse(window.sessionStorage.getItem(sessionKey)!)).toEqual(refreshedSession);
      expect(init?.headers).toEqual({ Authorization: "Bearer new-access-token" });
      return new Response(null, { status: 204 });
    });
    fireEvent.click(screen.getByRole("button", { name: /全セッションを失効/ }));
    await screen.findByText("ANONYMOUS");
    expect(fetchMock.mock.calls.map(([url]) => new URL(String(url)).pathname)).toEqual([
      "/auth/me", "/auth/signOutAll", "/auth/refresh", "/auth/signOutAll",
    ]);
    expect(fetchMock.mock.calls[1][1]?.headers).toEqual({ Authorization: "Bearer old-access-token" });
    expect(JSON.parse(fetchMock.mock.calls[2][1]!.body as string)).toEqual({ refreshToken: "old-refresh-token" });
    expect(window.sessionStorage.getItem(sessionKey)).toBeNull();
  });

  it("stops after a second 401", async () => {
    await restore();
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    fetchMock.mockResolvedValueOnce(Response.json(refreshedSession));
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    fireEvent.click(screen.getByRole("button", { name: /全セッションを失効/ }));
    await screen.findByRole("status");
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(JSON.parse(window.sessionStorage.getItem(sessionKey)!)).toEqual(refreshedSession);
  });

  it("does not resend if refresh fails", async () => {
    await restore();
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 503 }));
    fireEvent.click(screen.getByRole("button", { name: /全セッションを失効/ }));
    await screen.findByRole("status");
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(JSON.parse(window.sessionStorage.getItem(sessionKey)!)).toEqual(savedSession);
  });
});
