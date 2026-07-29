from rest_framework import serializers

from core.models import Activity


class ActivitySerializer(serializers.ModelSerializer):
    class Meta:
        model = Activity
        fields = ('id', 'actor', 'action', 'entity_type', 'entity_id', 'metadata', 'created_at')
        read_only_fields = fields
