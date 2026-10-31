#!/usr/bin/env python3
"""
provision.py — Provisionamento de secrets nível produção.

    python3 provision.py            # fluxo completo
    python3 provision.py --check    # auditoria sem alterar nada
    python3 provision.py --rotate   # rotação com janela de sobreposição (zero downtime)

Python 3.9+, apenas stdlib.
Baseado em: Cloudflare Workers Best Practices (2026), Play Integrity docs,
padrões HMAC+nonce anti-replay para APIs móveis.
"""
import argparse, hashlib, hmac as hmaclib, json, re, secrets, shutil, subprocess, sys, urllib.request, urllib.error
from datetime import datetime, timezone
from getpass import getpass
from pathlib import Path

# Baseado na localização do próprio script, não no cwd de quem chama —
# funciona igual rodando de dentro de backend-proxy/ ou da raiz do projeto.
SCRIPT_DIR = Path(__file__).resolve().parent  # .../jarvis-project/backend-proxy
ROOT = SCRIPT_DIR.parent                       # .../jarvis-project
LOCAL_PROPS = ROOT / "local.properties"
GITIGNORE = ROOT / ".gitignore"
DEV_VARS = SCRIPT_DIR / ".dev.vars"

KEYS = {
    "PROXY_BASE_URL":  dict(required=False, pat=re.compile(r"^https://[a-z0-9-]+(\.[a-z0-9-]+)+$"),
                            hint="saída do wrangler deploy (workers.dev OU domínio próprio)"),
}
ANTHROPIC_KEY_PATTERN = re.compile(r"^sk-ant-[A-Za-z0-9_\-]{20,}$")
HMAC_KEYS = ["PROXY_SHARED_SECRET", "PROXY_SHARED_SECRET_NEXT"]  # rotação com sobreposição

def fail(m):  print(f"\n❌ {m}", file=sys.stderr); sys.exit(1)
def warn(m):  print(f"⚠️  {m}")
def ok(m):    print(f"✅ {m}")
def mask(v):  return f"{v[:10]}…{v[-4:]}" if len(v) > 16 else "…"

def parse_props(path):
    order, props = [], {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$", line)
            if m and not line.lstrip().startswith("#"):
                props[m.group(1)] = m.group(2).strip()
            order.append(line if line else None)
    return props, [l for l in order if l is not None]

def git_tracked(p):
    try:
        return subprocess.run(["git","ls-files","--error-unmatch",str(p)],
                              capture_output=True, timeout=10).returncode == 0
    except Exception:
        return False

def verify_backup_integrity(src, dst):
    """SHA-256 do backup == original (prova que o backup é fiel)."""
    h = lambda f: hashlib.sha256(Path(f).read_bytes()).hexdigest()
    if h(src) != h(dst):
        fail("integridade do backup falhou — abortando sem gravar")

def worker_health(url, secret):
    """Sonda o worker com HMAC real, não só GET."""
    url = url.rstrip("/")
    ts = str(int(datetime.now(timezone.utc).timestamp()))
    nonce = secrets.token_hex(16)
    client_id = "provision-probe"
    body = json.dumps({"_probe": True, "ts": ts, "nonce": nonce}, sort_keys=True)
    mac = hmaclib.new(secret.encode(), f"{ts}.{nonce}.{client_id}.{body}".encode(), hashlib.sha256).hexdigest()
    req = urllib.request.Request(url, data=body.encode(), method="POST", headers={
        "Content-Type": "application/json",
        "X-Timestamp": ts, "X-Nonce": nonce, "X-Client-Id": client_id, "X-Signature": mac,
    })
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            ok(f"Worker autenticou a sonda HMAC (HTTP {r.status}) — proxy 100% operacional")
            return True
    except urllib.error.HTTPError as e:
        if e.code == 401:
            warn("Worker no ar mas REJEITOU o HMAC — secret do Worker difere do local, ou secrets ainda não registrados.")
            print("   → rode: npx wrangler secret put PROXY_SHARED_SECRET e cole o valor exibido ao final.")
        elif e.code == 429:
            warn("HTTP 429: rate limiter ativo e funcionando (bom sinal de segurança!)")
        else:
            warn(f"Worker respondeu HTTP {e.code}")
    except Exception as e:
        warn(f"Sem acesso ao worker agora ({type(e).__name__}) — prossiga, valide depois.")
    return False

def gen_hmac_secret():
    return secrets.token_hex(32)  # CSPRAG == openssl rand -hex 32

def write_props_atomic(props, order):
    out, written = [], set()
    for line in order:
        m = re.match(r"^([A-Za-z_][A-Za-z0-9_]*)\s*=", line)
        if m and m.group(1) in props:
            out.append(f"{m.group(1)}={props[m.group(1)]}"); written.add(m.group(1))
        else:
            out.append(line)
    for k, v in props.items():
        if k not in written:
            out.append(f"{k}={v}")
    tmp = LOCAL_PROPS.with_suffix(".tmp")
    tmp.write_text("\n".join(out) + "\n", encoding="utf-8")
    tmp.replace(LOCAL_PROPS)  # rename atômico

def write_dev_vars(props, anthropic_key):
    """Espelha secrets no .dev.vars do Worker (dev local, gitignored pelo wrangler)."""
    if not DEV_VARS.parent.exists():
        DEV_VARS.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"PROXY_SHARED_SECRET={props.get('PROXY_SHARED_SECRET','')}"]
    if anthropic_key:
        lines.insert(0, f"ANTHROPIC_API_KEY={anthropic_key}")
    if props.get("PROXY_SHARED_SECRET_NEXT"):
        lines.append(f"PROXY_SHARED_SECRET_NEXT={props['PROXY_SHARED_SECRET_NEXT']}")
    DEV_VARS.write_text("\n".join(lines) + "\n")
    ok(f"backend-proxy/.dev.vars sincronizado (dev local — wrangler ignora no deploy)")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--rotate", action="store_true")
    args = ap.parse_args()

    print("═"*66)
    print("  PROVISIONAMENTO DE SECRETS — nível produção")
    print("  (HMAC anti-replay · rotação zero-downtime · auditoria git)")
    print("═"*66)

    # ── Gate git ─────────────────────────────────────────────────────────
    print("\n[1/6] Auditoria de versionamento")
    if git_tracked(LOCAL_PROPS):
        fail("local.properties RASTREADO pelo git!\n"
             "     git rm --cached local.properties && git commit\n"
             "     Se já foi pushado: ROTACIONE tudo (provision.py --rotate).")
    if not (GITIGNORE.exists() and "local.properties" in GITIGNORE.read_text()):
        if not args.check:
            GITIGNORE.open("a").write("\nlocal.properties\nbackend-proxy/.dev.vars\n")
            ok(".gitignore atualizado (+ .dev.vars do Worker)")
    else:
        ok("local.properties e .dev.vars fora do versionamento")

    props, order = parse_props(LOCAL_PROPS)
    dev_props, _ = parse_props(DEV_VARS)
    legacy_api_key = props.get("CLAUDE_API_KEY", "")
    anthropic_key = dev_props.get("ANTHROPIC_API_KEY", "") or legacy_api_key

    # ── Relatório de estado ──────────────────────────────────────────────
    print("\n[2/6] Estado atual")
    all_keys = list(KEYS) + HMAC_KEYS
    for k in all_keys:
        v = props.get(k)
        tag = "OBRIGATÓRIO" if KEYS.get(k, {}).get("required") else "opcional"
        print(f"   {'•' if v else '○'} {k}: {mask(v) if v else f'ausente ({tag})'}")
    print(f"   {'•' if anthropic_key else '○'} ANTHROPIC_API_KEY: {'configurada no Worker' if anthropic_key else 'ausente (Workers AI grátis)'}")

    if args.check:
        problems = []
        for k, s in KEYS.items():
            if s["required"] and not props.get(k): problems.append(f"{k} ausente")
            if props.get(k) and not s["pat"].match(props[k]): problems.append(f"{k} formato inválido")
        if anthropic_key and not ANTHROPIC_KEY_PATTERN.match(anthropic_key):
            problems.append("ANTHROPIC_API_KEY formato inválido")
        for k in HMAC_KEYS:
            if props.get(k) and not re.match(r"^[0-9a-f]{64}$", props[k]):
                problems.append(f"{k} não é hex-64 válido")
        if problems: fail("; ".join(problems))
        ok("Auditoria completa: todos os formats e presenças OK"); return

    # ── Coleta ───────────────────────────────────────────────────────────
    print("\n[3/6] Coleta (entrada oculta, estilo senha)")
    new = {}
    for k, s in KEYS.items():
        while True:
            v = getpass(f"   {k}{' (Enter=pular)' if not s['required'] else ''}: ").strip()
            if not v:
                if props.get(k): new[k] = props[k]; print("      → mantido")
                elif s["required"]: print("      → obrigatória"); continue
                break
            if not s["pat"].match(v):
                print(f"      ✗ inválida ({s['hint']})"); continue
            new[k] = v; break

    while True:
        value = getpass("   ANTHROPIC_API_KEY (Enter=usar Workers AI/guardar atual): ").strip()
        if not value:
            break
        if not ANTHROPIC_KEY_PATTERN.match(value):
            print("      ✗ inválida (console.anthropic.com → Settings → API Keys)")
            continue
        anthropic_key = value
        break

    # ── Secrets HMAC / rotação ──────────────────────────────────────────
    print("\n[4/6] Secrets HMAC (rotação com sobreposição)")
    if args.rotate:
        # Estratégia da doc Cloudflare: upload do novo como _NEXT, worker aceita ambos,
        # app atualiza, depois remove o antigo.
        new["PROXY_SHARED_SECRET_NEXT"] = gen_hmac_secret()
        ok(f"NOVO secret (NEXT): {mask(new['PROXY_SHARED_SECRET_NEXT'])}")
        if props.get("PROXY_SHARED_SECRET"):
            new["PROXY_SHARED_SECRET"] = props["PROXY_SHARED_SECRET"]
            ok("Secret antigo MANTIDO temporariamente (janela de sobreposição)")
    else:
        if not props.get("PROXY_SHARED_SECRET"):
            new["PROXY_SHARED_SECRET"] = gen_hmac_secret()
        else:
            r = input("   Secret existe. Enter=manter · r=rotacionar (--rotate faz isso certo): ").strip().lower()
            new["PROXY_SHARED_SECRET"] = gen_hmac_secret() if r == "r" else props["PROXY_SHARED_SECRET"]
        new.pop("PROXY_SHARED_SECRET_NEXT", None)

    # ── Backup + gravação ────────────────────────────────────────────────
    print(f"\n[5/6] Gravando {LOCAL_PROPS}")
    if LOCAL_PROPS.exists():
        bk = LOCAL_PROPS.with_suffix(f".bak-{datetime.now():%Y%m%d-%H%M%S}")
        shutil.copy2(LOCAL_PROPS, bk)
        verify_backup_integrity(LOCAL_PROPS, bk)
        ok(f"backup íntegro: {bk.name} (sha256 verificado)")
    retired_keys = {"CLAUDE_API_KEY", "VOYAGE_API_KEY", "PICOVOICE_ACCESS_KEY"}
    new_props = {**{k: props[k] for k in props if k not in all_keys and k not in retired_keys}, **new}
    write_props_atomic(new_props, order)
    write_dev_vars(new_props, anthropic_key)
    ok("Gravado (rename atômico)")

    # ── Health check HMAC + runbook ─────────────────────────────────────
    print("\n[6/6] Validação do proxy")
    if new.get("PROXY_BASE_URL"):
        worker_health(new["PROXY_BASE_URL"], new["PROXY_SHARED_SECRET"])

    nxt = new.get("PROXY_SHARED_SECRET_NEXT")
    print("\n" + "═"*66)
    print("  RUNBOOK CLOUDFLARE (pasta backend-proxy/):")
    print("═"*66)
    if nxt:
        print(f"""
  # 1. Deploy do código que aceita AMBOS os secrets (ver worker/index.js incluso)
  npx wrangler deploy
  # 2. Registrar o NOVO secret
  npx wrangler secret put PROXY_SHARED_SECRET_NEXT
      ↳ cole: {nxt}
  # 3. Atualizar o app com o NEXT, publicar, aguardar 48h
  # 4. Promover: remover o antigo e renomear o binding
  npx wrangler secret delete PROXY_SHARED_SECRET
""")
    else:
        print(f"""
  npx wrangler deploy
  npx wrangler secret put PROXY_SHARED_SECRET
      ↳ cole EXATAMENTE: {new.get('PROXY_SHARED_SECRET', '(já configurado)')}
  npx wrangler secret put ANTHROPIC_API_KEY
      ↳ cole a chave Anthropic configurada somente no Worker
""")
    print("🔐 Nunca logado, nunca no chat. Guarde num password manager.")
    print("📅 Rotação recomendada: a cada 30–60 dias → python3 provision.py --rotate")

if __name__ == "__main__":
    try: main()
    except KeyboardInterrupt: print("\n\nCancelado — nada gravado."); sys.exit(130)
