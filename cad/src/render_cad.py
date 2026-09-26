from pathlib import Path
import json, vtk, numpy as np
from vtk.util.numpy_support import numpy_to_vtk, numpy_to_vtkIdTypeArray
root=Path(__file__).resolve().parents[2]
scene=json.loads((root/'cad/render/mesh_scene.json').read_text())

def render(filename,cutaway=False,eye=(1450,-1650,1220),size=(1600,1100)):
    ren=vtk.vtkRenderer(); ren.SetBackground(.956,.965,.975)
    for p in scene:
        if cutaway and p['name'] in ('conveyor_roof','conveyor_side_guard_-1','electronics_lid','removable_bin'): continue
        pts=vtk.vtkPoints(); pts.SetData(numpy_to_vtk(np.array(p['vertices'],dtype=float),deep=True))
        faces=np.array(p['triangles'],dtype=np.int64)
        arr=np.column_stack([np.full(len(faces),3),faces]).ravel()
        cells=vtk.vtkCellArray(); cells.SetCells(len(faces),numpy_to_vtkIdTypeArray(arr,deep=True))
        pd=vtk.vtkPolyData(); pd.SetPoints(pts); pd.SetPolys(cells)
        normals=vtk.vtkPolyDataNormals(); normals.SetInputData(pd); normals.ComputePointNormalsOn(); normals.SetFeatureAngle(45); normals.Update()
        mapper=vtk.vtkPolyDataMapper(); mapper.SetInputConnection(normals.GetOutputPort())
        actor=vtk.vtkActor(); actor.SetMapper(mapper); prop=actor.GetProperty(); prop.SetColor(*p['color']); prop.SetAmbient(.26); prop.SetDiffuse(.74); prop.SetSpecular(.2)
        ren.AddActor(actor)
    cam=ren.GetActiveCamera(); cam.SetPosition(*eye); cam.SetFocalPoint(20,0,310); cam.SetViewUp(0,0,1); cam.ParallelProjectionOn(); cam.SetParallelScale(495)
    light=vtk.vtkLight(); light.SetPosition(900,-1200,2000); light.SetFocalPoint(0,0,250); light.SetIntensity(.8); ren.AddLight(light)
    rw=vtk.vtkRenderWindow(); rw.SetOffScreenRendering(1); rw.SetSize(*size); rw.AddRenderer(ren); rw.SetMultiSamples(4); ren.ResetCameraClippingRange(); rw.Render()
    out=vtk.vtkWindowToImageFilter(); out.SetInput(rw); out.SetInputBufferTypeToRGB(); out.ReadFrontBufferOff(); out.Update()
    w=vtk.vtkPNGWriter(); w.SetFileName(str(root/'cad/render'/filename)); w.SetInputConnection(out.GetOutputPort()); w.Write(); rw.Finalize()
render('assembly.png')
render('cutaway.png',True)
render('rear.png',False,(-1600,-1700,1150))
