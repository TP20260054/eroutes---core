# Carga del grafo vial

Desde la raíz del backend, crea y activa el entorno virtual e instala las dependencias:

```powershell
python -m venv data\.venv
data\.venv\Scripts\Activate.ps1
pip install -r data\requirements.txt
```

Ejecuta la carga con `.env.local` en la raíz:

```powershell
python data\cargar_grafo.py --place "San Isidro, Lima, Peru" --reset
```

El script descarga el grafo vial de OpenStreetMap mediante OSMnx y carga sus nodos y tramos en Neon usando `DATABASE_URL_UNPOOLED`.
El flag `--reset` vacía `tramos` y `nodos` antes de insertar el grafo descargado.
