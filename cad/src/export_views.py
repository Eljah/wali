#!/usr/bin/env python3
"""Orthographic CAD views with Z up; avoids gp_Ax2's arbitrary roll for side normals."""
from pathlib import Path
import cadquery as cq
from cadquery import exporters

def export_views(shape: cq.Shape, directory: Path) -> None:
    # Use the XY projector after rigid coordinate transformations, never pixel redrawing.
    # Side: screen (X,Z), camera at -Y. Front: screen (Y,Z), camera at +X.
    views = {
        'top': (shape, (0, 0, 1)),
        'side': (shape.rotate((0,0,0),(1,0,0),-90), (0,0,1)),
        'front': (shape.rotate((0,0,0),(0,0,1),-90).rotate((0,0,0),(1,0,0),-90), (0,0,1)),
        'iso': (shape, (1,-1,1)),
    }
    directory.mkdir(parents=True, exist_ok=True)
    for name, (oriented, direction) in views.items():
        exporters.export(oriented, str(directory / (name + '.svg')), opt={
            'width':1100, 'height':780, 'marginLeft':25, 'marginTop':25,
            'projectionDir':direction, 'showAxes':False, 'showHidden':False,
            'strokeWidth':1.0,
        })

if __name__ == '__main__':
    cad = Path(__file__).resolve().parents[1]
    shape = cq.importers.importStep(str(cad/'step/UBOR_JAVA_R01_assembly.step')).val()
    export_views(shape, cad/'drawings')
    print('4 exact CAD views; side/front use Z-up orientation')
