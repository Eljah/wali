from pathlib import Path
import cadquery as cq,json
root=Path(__file__).resolve().parents[1]
shape=cq.importers.importStep(str(root/'cad/step/UBOR_JAVA_R01_assembly.step')).val()
bb=shape.BoundingBox();result={'check':'STEP round-trip with OpenCascade, not SolidWorks','valid':shape.isValid(),'solid_count':len(shape.Solids()),'bbox_mm':[bb.xlen,bb.ylen,bb.zlen]}
assert result['valid'] and result['solid_count']==json.loads((root/'verification/cad_checks.json').read_text())['total_solids'],result
(root/'verification/step_roundtrip.json').write_text(json.dumps(result,indent=2))
print(result)
