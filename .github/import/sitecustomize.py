"""Download diagnostics. Never log a signed URL, response body, or credentials."""
import re
import urllib.error
import urllib.request

_original = urllib.request.urlopen

def _urlopen(request, *args, **kwargs):
    url = request if isinstance(request, str) else request.full_url
    artifact = '.oaiusercontent.com/' in url
    if artifact and isinstance(request, str):
        request = urllib.request.Request(request, headers={'User-Agent': 'Mozilla/5.0 wali-project-import', 'Accept': 'application/octet-stream'})
    try:
        return _original(request, *args, **kwargs)
    except urllib.error.HTTPError as error:
        if artifact:
            body = error.read(8192).decode('utf-8', errors='replace')
            code = re.search(r'<Code>([A-Za-z0-9_]+)</Code>', body)
            print('Artifact HTTP status: %s; storage error: %s; server: %s' %
                  (error.code, code.group(1) if code else 'not supplied', error.headers.get('Server', 'not supplied')), flush=True)
        raise
    except urllib.error.URLError as error:
        if artifact:
            print('Artifact network failure type: %s' % type(error.reason).__name__, flush=True)
        raise

urllib.request.urlopen = _urlopen
