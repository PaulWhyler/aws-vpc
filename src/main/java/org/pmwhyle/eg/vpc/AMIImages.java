package org.pmwhyle.eg.vpc;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import org.joda.time.DateTime;

import java.util.List;
import java.util.Optional;

/**
 * The named types of images used by this VPC, provides filters
 * for finding the image ids.
 */
enum AMIImages {

    ECSImage("*amzn-ami-*-amazon-ecs-optimized*"),
    NATImage("*amzn-ami-vpc-nat*");

    public final String imageName;

    AMIImages(String imageName) {
        this.imageName = imageName;
    }

    static String imageIdFor(AmazonEC2Client ec2Client, AMIImages image) {
        List<Image> images = ec2Client.describeImages(
                new DescribeImagesRequest()
                        .withOwners("amazon")
                        .withFilters(image.getFilter()))
                .getImages();
        Optional<String> imageId = images.stream()
                .filter(i -> "hvm".equals(i.getVirtualizationType()))
                .sorted(AMIImages::reverseCompareCreation)
                .map(Image::getImageId)
                .findFirst();
        return imageId.orElseThrow(() -> new NoImageFoundException(image.toString()));
    }

    private Filter getFilter() {
        return new Filter("name").withValues(imageName);
    }

    private static int reverseCompareCreation(Image i1, Image i2) {
            return new DateTime(i2.getCreationDate()).compareTo(new DateTime(i1.getCreationDate()));
        }

    private static class NoImageFoundException extends RuntimeException {
        NoImageFoundException(String image) {
            super("No image for " + image + " could be found");
        }
    }

}
