// Builds Arrakis_Primrose.blockymodel: five copies of the vanilla Cactus Flower model (Cactus.blockymodel) arranged on
// one block. The copies keep their full size here; the block asset scales the whole model down (CustomModelScale), so a
// cluster of five fits inside one block. Each copy gets its own yaw so the cluster does not look stamped.
//
//   node tools/assets/make_primrose.js <path to vanilla Common/Blocks/Foliage/Flowers/Cactus.blockymodel>
const fs = require('fs');
const path = require('path');

const source = process.argv[2];
if (!source) {
    console.error('usage: node make_primrose.js <vanilla Cactus.blockymodel>');
    process.exit(1);
}
const model = JSON.parse(fs.readFileSync(source, 'utf8'));
const root = model.nodes[0];

// Offsets of the five flowers in model units (before the block scale), centre first.
const OFFSETS = [[0, 0], [-22, -20], [21, -22], [-21, 22], [22, 21]];
// Yaw of each copy, degrees.
const YAWS = [0, 70, 140, 215, 290];

let nextId = 1;
function copy(node, dx, dz, yaw) {
    const c = JSON.parse(JSON.stringify(node));
    (function renumber(n, top) {
        n.id = String(nextId++);
        if (top) {
            n.position.x += dx;
            n.position.z += dz;
            const h = (yaw * Math.PI / 180) / 2;
            n.orientation = { x: 0, y: Math.sin(h), z: 0, w: Math.cos(h) };
        }
        (n.children || []).forEach(child => renumber(child, false));
    })(c, true);
    return c;
}

const out = { ...model, nodes: OFFSETS.map(([dx, dz], i) => copy(root, dx, dz, YAWS[i])) };
const target = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'Common', 'Blocks', 'Water_of_Arrakis',
    'Arrakis_Primrose.blockymodel');
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.writeFileSync(target, JSON.stringify(out, null, 2));
console.log('wrote', target, 'with', out.nodes.length, 'flowers,', nextId - 1, 'nodes');
