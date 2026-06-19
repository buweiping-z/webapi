using System.ComponentModel.DataAnnotations.Schema;

namespace webapi.Models
{
    [Table("inspection_photos")]
    public class InspectionPhoto
    {
        [Column("id")]
        public int Id { get; set; }

        [Column("record_id")]
        public int RecordId { get; set; }

        [Column("item_name")]
        public string ItemName { get; set; } = string.Empty;

        [Column("photo_path")]
        public string PhotoPath { get; set; } = string.Empty;

        [Column("thumbnail_path")]
        public string? ThumbnailPath { get; set; }

        [Column("photo_order")]
        public int PhotoOrder { get; set; } = 0;

        [Column("uploaded_by")]
        public string UploadedBy { get; set; } = string.Empty;

        [Column("created_at")]
        public DateTime CreatedAt { get; set; }
    }
}
