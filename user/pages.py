"""Tiny self-contained HTML pages shown when someone opens an emailed link in a browser."""
from django.http import HttpResponse
from django.utils.html import escape

APP_SCHEME = 'splitease'

_STYLE = """
body{margin:0;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;background:#F8FAFB;color:#0F172A;
display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}
.card{background:#fff;border-radius:20px;padding:32px 24px;max-width:420px;width:100%;text-align:center;
box-shadow:0 2px 12px rgba(15,23,42,.08)}
.logo{width:56px;height:56px;border-radius:16px;background:#0F766E;margin:0 auto 16px;display:flex;align-items:center;justify-content:center}
h1{font-size:22px;margin:0 0 8px}p{color:#475569;line-height:1.5;margin:0 0 20px}
a.btn,button.btn{display:block;box-sizing:border-box;background:#0F766E;color:#fff;text-decoration:none;border:0;font-size:16px;font-weight:600;
padding:14px;border-radius:14px;margin-top:12px;width:100%;cursor:pointer}
a.ghost{background:#F1F5F9;color:#0F172A}
input{width:100%;box-sizing:border-box;padding:14px;border:1.5px solid #E2E8F0;border-radius:12px;font-size:16px;margin-bottom:12px}
.err{color:#DC2626;font-size:14px;margin-bottom:12px}
"""


def page(title, message, buttons=(), body_html='', status=200):
    """`buttons` is a sequence of (label, url, primary) triples."""
    links = ''.join(
        f'<a class="btn{"" if primary else " ghost"}" href="{escape(url)}">{escape(label)}</a>'
        for label, url, primary in buttons
    )
    html = (
        '<!doctype html><html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        f'<title>{escape(title)} · SplitEase</title><style>{_STYLE}</style></head><body><div class="card">'
        '<div class="logo"><svg width="30" height="30" viewBox="0 0 48 48"><path d="M9.9 9.9A20 20 0 0 1 38.1 38.1Z" fill="#F59E0B"/>'
        '<path d="M38.1 38.1A20 20 0 0 1 9.9 9.9Z" fill="#fff"/></svg></div>'
        f'<h1>{escape(title)}</h1><p>{escape(message)}</p>{body_html}{links}</div></body></html>'
    )
    return HttpResponse(html, status=status)


def app_link(path=''):
    return f'{APP_SCHEME}://{path}'
