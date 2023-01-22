from openpyxl import load_workbook
from os import path
from io import BytesIO
import sys
import urllib.request

# Charge un fichier Excel depuis url


def load_workbook_from_url(url):
    file = urllib.request.urlopen(url).read()
    return load_workbook(filename=BytesIO(file))

# Charge le dernier jeu de données et l'importe dans le modèle


# Fichiers Excel
srcWb = load_workbook_from_url(
    'https://bourse.societegenerale.fr/EmcWebApi/api/ProductSearch/Export?PageNum=1&ProductClassificationId=19&AssetTypeId=2&AssetTypeMenuId=35')
# srcWb = load_workbook(path.join(sys.path[0], 'export.xlsx'))
modWb = load_workbook(path.join(sys.path[0], 'modele.xlsx'))
# Onglets Export
srcWs = srcWb['EXPORT']
modWs = modWb['EXPORT']
# Copie les données de l'onglet Export du fichier source vers le modèle
for row in srcWs:
    for cell in row:
        modWs[cell.coordinate].value = cell.value

modWb.save(path.join(sys.path[0], 'resultat.xlsx'))
