import io
import uuid

from django.core.files.base import ContentFile
from django.utils.deconstruct import deconstructible
from PIL import Image, ImageOps, UnidentifiedImageError
from rest_framework import serializers

MAX_UPLOAD_BYTES = 5 * 1024 * 1024
MAX_SIDE = 512


@deconstructible
class RandomName:
    """upload_to callable: unguessable file names, since media URLs are public."""

    def __init__(self, folder):
        self.folder = folder

    def __call__(self, instance, filename):
        return f'{self.folder}/{uuid.uuid4().hex}.jpg'


def clean_image(upload):
    """Validates an uploaded picture and re-encodes it as a small JPEG.

    Re-encoding drops EXIF data (such as GPS position) and anything that is not really an image.
    """
    if upload is None:
        raise serializers.ValidationError({'image': 'Choose a picture to upload.'})
    if upload.size > MAX_UPLOAD_BYTES:
        raise serializers.ValidationError({'image': 'The picture is too large (5 MB maximum).'})
    try:
        image = Image.open(upload)
        image.verify()
        upload.seek(0)
        image = ImageOps.exif_transpose(Image.open(upload))
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError):
        raise serializers.ValidationError({'image': 'That file is not a valid picture.'})
    image = image.convert('RGB')
    image.thumbnail((MAX_SIDE, MAX_SIDE))
    buffer = io.BytesIO()
    image.save(buffer, 'JPEG', quality=85, optimize=True)
    return ContentFile(buffer.getvalue(), name='image.jpg')
