"""Parser XMLTV para guías EPG públicas."""
from datetime import datetime, timezone
from xml.etree import ElementTree as ET


def parse_xmltv(raw: bytes, limit: int = 100000):
    root = ET.fromstring(raw)
    rows = []
    for node in root.findall("programme")[:limit]:
        try:
            start = datetime.strptime(node.attrib["start"][:14], "%Y%m%d%H%M%S").replace(tzinfo=timezone.utc)
            stop = datetime.strptime(node.attrib["stop"][:14], "%Y%m%d%H%M%S").replace(tzinfo=timezone.utc)
        except (KeyError, ValueError):
            continue
        title = node.findtext("title", default="").strip()
        if not title:
            continue
        rows.append({"tvg_id": node.attrib.get("channel", ""), "titulo": title,
                     "descripcion": node.findtext("desc", default="").strip(),
                     "inicio": start.replace(tzinfo=None), "fin": stop.replace(tzinfo=None)})
    return rows
